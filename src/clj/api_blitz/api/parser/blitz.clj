(ns api-blitz.api.parser.blitz
  (:require
   [taoensso.timbre :as timbre]
   [clojure.zip :as zip]
   [clojure
    [zip :as zip]
    [string :as s]]
   [clj-time
    [core :as tc]
    [format :as tf]
    [local :as tl]
    [coerce :as tcc]]
   [hickory [core :as hc]
    [utils  :as hu]
    [zip    :as hz]
    [select :as hs]
     [render :as hr]]))

(def config
  (let [base-url "https://www.cgvblitz.com"]
    {:base-url base-url
     :homepage (str base-url "/en")
     :schedule-movie (str base-url "/en/schedule/movie")
     :schedule-cinema (str base-url "/en/schedule/cinema")
     :schedule-seat (str base-url "/en/schedule/seat")
     :movies-playing (str base-url "/en/movies/now_playing")

     :hc-params {:timeout 240000 ; 2 minutes
                 :user-agent "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:39.0) Gecko/20100101 Firefox/39.0"
                 :keep-alive 60000
                 :follow-redirects false
                 :as :text}}))

(defn get-cookie [headers]
  (some-> headers
          :set-cookie
          (s/split #",")
          (as-> xs
              (->> xs
                   (filter not-empty)
                   (map #(-> % (s/split #"=" 2) (update 0 keyword)))
                   (into {})))))

(defn assoc-cookie [cookie-map headers]
  "Assoc given cookie-map to headers."
  (let [cookie-seq (map (fn [[k v]]
                          (str (name k)
                               "="
                               (first
                                (s/split v #";"))))
                        cookie-map)]
    (merge-with merge headers {:headers {"Cookie" (s/join "; " cookie-seq)}})))

(defn merge-cookie [cookie-map headers]
  "Merge cookie-map with cookie found in headers.
   Internally use `get-cookie` to get the cookie in the headers."
  (merge cookie-map (get-cookie headers)))

(defn parse-movie
  [tag-li]
  (let [movie-img (->> tag-li
                       (hs/select
                        (hs/tag :img))
                       first :attrs :src)
        movie-id (->> tag-li
                      (hs/select
                       (hs/and
                        (hs/tag :a)
                        (hs/class "sel-body-play")))
                      first :attrs :href)
        movie-id' (some-> movie-id
                          (s/split #"\/")
                          last)]
    {:movie-img (str (:base-url config) movie-img)
     :movie movie-id'}))

(defn parse-get-movies
  [body]
  (try
    (let [hick-html (-> body hc/parse hc/as-hickory)
          movie-list (hs/select
                      (hs/descendant
                       (hs/and
                        (hs/tag :div)
                        (hs/class "movie-list-body"))
                       (hs/tag :ul)
                       (hs/tag :li))
                      hick-html)
          comingsoon-movie-list (hs/select
                                 (hs/descendant
                                  (hs/and
                                   (hs/tag :div)
                                   (hs/class "comingsoon-movie-list-body"))
                                  (hs/tag :ul)
                                  (hs/tag :li))
                                 hick-html)]
      (array-map
       :movie-list (mapv parse-movie movie-list)
       :movie-comingsoon (mapv parse-movie comingsoon-movie-list)))
    
    (catch Throwable e
      (timbre/debug "error while parse-movies" (.getMessage e))
      (throw (Exception. "Error Parse Movies")))))

(defn parse-schedule-movie-showtime
  [tag-movie-showtime]
  (let [attrs-tag (some-> tag-movie-showtime :content first :attrs)
        movie-time (some-> tag-movie-showtime :content first :content first)]
    (assoc attrs-tag :movie-time movie-time)))

(defn parse-schedule-movie-type
  [tag-schedule-type]
  (let [schedule-type (-> tag-schedule-type
                          first :content
                          last s/trim)
        movie-showtime (->> tag-schedule-type
                            second
                            (hs/select
                             (hs/descendant
                              (hs/and
                               (hs/tag :ul)
                               (hs/class "showtime-lists"))
                              (hs/tag :li))))]
    (timbre/debug "TAG-SCHEDULE" (pr-str schedule-type))
    (array-map
     :schedule-type schedule-type
     :movie-showtime (mapv parse-schedule-movie-showtime movie-showtime))))

(defn parse-schedule-movie-list
  [tag-schedule-list]
  (let [raw-schedule-title (->> tag-schedule-list
                                (hs/select
                                 (hs/class "schedule-title"))
                                first :content)
        raw-schedule-type (->> tag-schedule-list
                                (hs/select
                                 (hs/tag :ul))
                                first :content
                                (filter #(map? %))
                                (partition 2))
        schedule-cinema (first raw-schedule-title)
        description (-> raw-schedule-title
                        last :content last)
        ;; movie-id (some-> raw-schedule-title
        ;;                  last
        ;;                  :content first :attrs :href
        ;;                  (s/split #"\/") last)
        ;; movie-title (-> raw-schedule-title
        ;;                 last :content
        ;;                 first :content first)
        ]
    (when-not (nil? schedule-cinema)
      (let [movie-details (mapv parse-schedule-movie-type raw-schedule-type)
            cinematitle (-> movie-details first :movie-showtime first :cinematitle)
            movie-id (-> movie-details first :movie-showtime first :movie)
            movie-title (-> movie-details first :movie-showtime first :movietitle)
            
            [movie-type duration since]
            (->> (s/split description #"\/")
                 (remove s/blank?)
                 (mapv s/trim))]
        (array-map
         :cinematitle cinematitle
         :movie movie-id
         :movie-title movie-title
         :movie-type movie-type
         :duration duration
         :since since
         :movie-details movie-details)))))

(defn parse-schedule-movie
  [body]
  (try
    (let [hick-html (-> body hc/parse hc/as-hickory)
          schedule-list (hs/select
                         (hs/descendant
                          (hs/and
                           (hs/tag :div)
                           (hs/class "schedule-lists"))
                          (hs/tag :ul)
                          (hs/tag :li))
                         hick-html)
          result (->> (mapv parse-schedule-movie-list schedule-list)
                      (remove nil?))]
      result)
    (catch Throwable e
      (timbre/debug "error while parse-schedule-movie" (.getMessage e))
      (throw (Exception. "Error Parse Schedule Movie")))))

(defn parse-seat-row
  [tag-seat-row]
  (let [tag-seat-row (hs/select
                      (hs/tag :li)
                      tag-seat-row)]
    (mapv (fn [coll]
            (let [tag-seat-row-a (hs/select
                                  (hs/tag :a)
                                  coll)
                  seat-id (->> tag-seat-row-a
                               first :content first)
                  seat-taken? (->> tag-seat-row-a
                                   first :attrs :class
                                   (or "")
                                   (re-find #"taken")
                                   (nil?) not)]
              (array-map
               :seat-id seat-id
               :seat-taken seat-taken?)))
          tag-seat-row)))

(defn parse-schedule-audit-seats
  [body]
  (try
    (let [hick-html (-> body hc/parse hc/as-hickory)
          seat-rows (hs/select
                    (hs/descendant
                     (hs/and
                      (hs/tag :div)
                      (hs/class "audi-seats"))
                     (hs/and
                      (hs/tag :ul)
                      (hs/class "seat-row")))
                    hick-html)]
      (mapv parse-seat-row seat-rows))
    (catch Throwable e
      (timbre/debug "error while parse-schedule-seat" (.getMessage e))
      (throw (Exception. "Error Parse Schedule Seat")))))

