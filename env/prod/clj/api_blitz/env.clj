(ns api-blitz.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[api-blitz started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[api-blitz has shut down successfully]=-"))
   :middleware identity})
