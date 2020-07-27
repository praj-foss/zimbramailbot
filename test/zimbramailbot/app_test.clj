(ns zimbramailbot.app-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [org.httpkit.server :as s]
            [compojure.core :refer [routes GET]]
            [compojure.route :as route]
            [cheshire.core :as json]
            [zimbramailbot.app :refer :all]))

(def ^:private mock-telegram
  (wrap-defaults
   (routes (GET "/setWebhook" {body :body content :content-type}
                (if (= "application/json" content)
                  {:status 200 :body body}
                  {:status 400}))
           (route/not-found "Invalid"))
   api-defaults))

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

(defn- mock-update [user-id text]
  (json/generate-string
   {"update_id" 1000
    "message"
    {"date" 1441645532
     "chat" {"last_name"  "TestLast"
             "id"         user-id
             "type"       "private"
             "first_name" "TestFirst"
             "username"   "TestUser"}
     "message_id" 1365
     "from" {"last_name"  "TestLast"
             "id"         user-id
             "first_name" "TestFirst"
             "username"   "TestUser"}
     "text" text}}))

(deftest test-parser
  (testing "valid commands"
    (doseq [[text command] {"/start"  :start
                            "/help"   :help
                            "/login"  :login
                            "/logout" :logout}]
      (is (= {:user 123 :command command}
             (parse-update (mock-update 123 text))))))
  (testing "invalid commands"
    (doseq [text ["/INVALID"
                  "/more invalid"
                  "more invalid"
                  "//quit"]]
      (is (= {:user 456 :command nil}
             (parse-update (mock-update 456 text)))))))
