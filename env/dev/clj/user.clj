(ns user
  (:require [mount.core :as mount]
            api-blitz.core))

(defn start []
  (mount/start-without #'api-blitz.core/repl-server))

(defn stop []
  (mount/stop-except #'api-blitz.core/repl-server))

(defn restart []
  (stop)
  (start))


