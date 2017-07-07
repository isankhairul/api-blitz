(ns api-blitz.routes.home
  (:require [api-blitz.layout :as layout]
            [compojure.core :refer [defroutes GET POST ANY]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [cheshire.core :as ches]
            [api-blitz.api.core :as ac]))

(defn home-page []
  (layout/render
    "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn about-page []
  (layout/render "about.html"))

(defn -read-json-body-stream
  [ctx]
  (if-let [json (some-> ctx :body io/reader
                        (ches/decode-stream true))]
    (assoc ctx :body json)
    ctx))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page))
  (GET "/api/get_movies" ctx (-> ctx -read-json-body-stream (ac/service-handler #'ac/perform-get-movies)))
  (POST "/api/schedule/movie" ctx (-> ctx -read-json-body-stream (ac/service-handler #'ac/perform-schedule-movie)))
  (POST "/api/schedule/cinema" ctx (-> ctx -read-json-body-stream (ac/service-handler #'ac/perform-schedule-cinema)))
  (POST "/api/schedule/seat" ctx (-> ctx -read-json-body-stream (ac/service-handler #'ac/perform-schedule-seat))))

