(ns chat.app
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(defroutes app-routes
  (route/not-found "Not Found"))

(def handler
  (wrap-defaults app-routes site-defaults))
