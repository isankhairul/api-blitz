(ns api-blitz.api.core
  (:require [taoensso.timbre :as timbre]
            [clojure.zip :as zip]
            [org.httpkit.client :as http-client]
            [cheshire.core :as ches]
            [api-blitz.api.parser.blitz :as pb]
            [clojure
             [zip :as zip]
             [string :as s]]
            [clj-time
             [core :as tc]
             [format :as tf]
             [local :as tl]
             [coerce :as tcc]]
            [hickory
              [core :as hc]
              [utils  :as hu]
              [zip    :as hz]
              [select :as hs]
              [render :as hr]]))

(defn perform-actual-get-homepage []
  (let [hc-params (pb/config :hc-params)
        cookie-map {}
        {:keys [status headers body error] :as resp}
        @(http-client/get (pb/config :homepage)
                          (pb/assoc-cookie cookie-map hc-params))
        cookie-map (merge cookie-map (pb/get-cookie headers))
        state-map {:session-ctx {:cookies cookie-map}}]
    state-map))

(defn perform-actual-get-movies [state-map]
  (timbre/debug "running get-movies...")
  (let [hc-params (pb/config :hc-params)
        hc-params (assoc hc-params
                         :headers {"Referer" (pb/config :homepage)})
        cookie-map (get-in state-map [:session-ctx :cookies])
        {:keys [status headers body error] :as resp}
        @(http-client/get (pb/config :movies-playing)
                          (pb/assoc-cookie cookie-map hc-params))
        _ (spit (str "/tmp/get-movies-" (System/currentTimeMillis) ".html")
                body)
        cookie-map (merge cookie-map (pb/get-cookie headers))
        state-map {:session-ctx {:cookies cookie-map}}
        parse-movie (pb/parse-get-movies body)]
    
    [state-map parse-movie]))

(defn perform-actual-schedule-movie
  [state-map params]
  (timbre/debug "running schedule-movie..." params)
  (let [hc-params (pb/config :hc-params)
        hc-params (assoc hc-params
                         :headers {"Referer" (pb/config :movies-playing)})
        cookie-map (get-in state-map [:session-ctx :cookies])

        url-schedule-movie (str (pb/config :schedule-movie) "/" (:movie params) "/" (:showdate params))
        _ (timbre/debug "URL" url-schedule-movie)
        
        {:keys [status headers body error] :as resp}
        @(http-client/get url-schedule-movie
                          (pb/assoc-cookie cookie-map hc-params))
        _ (spit (str "/tmp/schedule-movies-" (System/currentTimeMillis) ".html")
                body)
        cookie-map (merge cookie-map (pb/get-cookie headers))
        state-map {:session-ctx {:cookies cookie-map}}
        parse-movie (pb/parse-schedule-movie body)]
    
    [state-map parse-movie]))

(defn perform-actual-schedule-cinema
  [state-map params]
  (timbre/debug "running schedule-cinema..." params)
  (let [hc-params (pb/config :hc-params)
        hc-params (assoc hc-params
                         :headers {"Referer" (pb/config :homepage)})
        cookie-map (get-in state-map [:session-ctx :cookies])

        url-schedule-cinema (str (pb/config :schedule-cinema) "/" (:cinema params) "/" (:showdate params))
        _ (timbre/debug "URL" url-schedule-cinema)
        
        {:keys [status headers body error] :as resp}
        @(http-client/get url-schedule-cinema
                          (pb/assoc-cookie cookie-map hc-params))
        _ (spit (str "/tmp/schedule-cinema-" (System/currentTimeMillis) ".html")
                body)
        cookie-map (merge cookie-map (pb/get-cookie headers))
        state-map {:session-ctx {:cookies cookie-map}}
        parse-movie (pb/parse-schedule-movie body)]
    
    [state-map parse-movie]))

(defn perform-actual-schedule-seat
  [state-map params]
  (timbre/debug "running schedule-seat..." params)
  (let [hc-params (pb/config :hc-params)

        schedule-cinema? (= (s/lower-case (:from-schedule params))
                            "cinema")
        url-schedule-refer (if schedule-cinema?
                             (str (pb/config :schedule-cinema) "/" (:cinema params) "/" (:showdate params))
                             (str (pb/config :schedule-movie) "/" (:cinema params) "/" (:showdate params)))
        
        hc-params (assoc hc-params
                         :headers {"Referer" url-schedule-refer})
        cookie-map (get-in state-map [:session-ctx :cookies])

        url-schedule-seat (str (:schedule-seat pb/config) "?"
                               (ring.util.codec/form-encode params))
        _ (timbre/debug "URL" url-schedule-seat)
        
        {:keys [status headers body error] :as resp}
        @(http-client/get url-schedule-seat
                          (pb/assoc-cookie cookie-map hc-params))
        _ (spit (str "/tmp/schedule-seat-" (System/currentTimeMillis) ".html")
                body)
        cookie-map (merge cookie-map (pb/get-cookie headers))
        state-map {:session-ctx {:cookies cookie-map}}
        parse-seat (->> (pb/parse-schedule-audit-seats body)
                        (remove empty?))]
    
    [state-map parse-seat]))

(defn perform-get-movies
  [params]
  (let [state-home (perform-actual-get-homepage)
        [state-map resp]
        (perform-actual-get-movies state-home)]
    resp))

(defn perform-schedule-movie
  [params]
  (let [state-home (perform-actual-get-homepage)
        [state-map resp-get-movies]
        (perform-actual-get-movies state-home)
        
        [state-map resp-schedule-movie]
        (perform-actual-schedule-movie state-map params)]
    resp-schedule-movie))

(defn perform-schedule-cinema
  [params]
  (let [state-home (perform-actual-get-homepage)
        [state-map resp-schedule-cinema]
        (perform-actual-schedule-cinema state-home params)]
    resp-schedule-cinema))

(defn perform-schedule-seat
  [params]
  (let [state-home (perform-actual-get-homepage)
        from-schedule (:from-schedule params)

        [state-map resp-schedule-cinema]
        (if (= from-schedule "cinema")
          (perform-actual-schedule-cinema state-home params)
          (perform-actual-schedule-movie state-home params))
        [state-map resp-schedule-seat]
        (perform-actual-schedule-seat state-map params)]
    resp-schedule-seat))

(defn service-handler
  [ctx handler]
  (try
    (let [body (:body ctx)
          result (handler body)
          response (ches/generate-string
                    {:error-status 0
                     :result result})]
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body response})
    
    (catch Throwable e
      (let [response {:error-status 1
                      :error-msg (or (:error-msg (ex-data e))
                                     (.getMessage e))}]
        (timbre/debug "Error handle" (pr-str response))
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body (ches/generate-string response)}))))
