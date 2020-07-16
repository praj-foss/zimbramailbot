(ns chat.app-test
  (:require [clojure.test :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [org.httpkit.server :as s]
            [compojure.core :refer [routes GET]]
            [compojure.route :as route]
            [cheshire.core :as json]
            [chat.app :refer :all]))

(def mock-telegram
  (wrap-defaults
   (routes (GET "/setWebhook" {body :body content :content-type}
                (if (= "application/json" content)
                  {:status 200 :body body}
                  {:status 400}))
           (route/not-found "Invalid"))
   site-defaults))

(deftest test-set-webhook
  (let [stopper (s/run-server mock-telegram {:port 8080})]
    (try (let [res (set-webhook "http://localhost:8080" "example")]
           (is (= 200 (:status res))
               "Must receive HTTP OK on proper Content-Type")
           (is (= {"url" "example"} (json/parse-string (:body res)))
               "Must include webhook url in request body"))
         (finally (stopper)))))
