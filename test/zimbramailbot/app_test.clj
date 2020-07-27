(ns zimbramailbot.app-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [org.httpkit.server :as s]
            [compojure.core :refer [routes GET]]
            [compojure.route :as route]
            [cheshire.core :as json]
            [zimbramailbot.app :refer :all]))

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

(deftest test-update
  (let [status-for (fn [ip]
                     (-> (mock/request :post "/updates")
                         (mock/header "X-Forwarded-For" ip)
                         (handler)
                         (:status)))]
    (testing "authorized Telegram IPs"
      (is (= 200 (status-for "149.154.160.0")))
      (is (= 200 (status-for "149.154.175.255")))
      (is (= 200 (status-for "91.108.4.0")))
      (is (= 200 (status-for "91.108.7.255"))))
    (testing "other random IPs"
      (is (= 404 (status-for "172.217.167.164")))
      (is (= 404 (status-for "104.91.50.11")))
      (is (= 404 (status-for "2001:db8:85a3:8d3:1319:8a2e:370:7348")))
      (is (= 404 (:status
                  (handler (mock/request :post "/updates"))))))))
