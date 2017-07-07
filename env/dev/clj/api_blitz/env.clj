(ns api-blitz.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [api-blitz.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[api-blitz started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[api-blitz has shut down successfully]=-"))
   :middleware wrap-dev})
