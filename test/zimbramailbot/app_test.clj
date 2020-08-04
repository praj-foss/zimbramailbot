(ns zimbramailbot.app-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as y]
            [ring.mock.request :as mock]
            [ring.util.response :as res]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [org.httpkit.server :as s]
            [compojure.core :refer [routes GET POST]]
            [compojure.route :as route]
            [cheshire.core :as json]
            [zimbramailbot.app :refer :all]))

(def ^:private mock-telegram
  (wrap-defaults
   (routes
    (POST "/setWebhook"
          {body-json :body content :content-type}
          (let [body (json/parse-string (slurp body-json))]
            (if (and (= "application/json" content)
                     (contains? body "url"))
              (-> (json/generate-string body)
                  (res/response)
                  (res/content-type content)
                  (res/status 200))
              (res/status 400))))
    (POST "/sendMessage"
          {body-json :body content :content-type}
          (let [body (json/parse-string (slurp body-json))]
            (if (and (= "application/json" content)
                     (contains? body "chat_id")
                     (contains? body "text"))
              (-> (json/generate-string body)
                  (res/response)
                  (res/content-type "application/json")
                  (res/status 200))
              (res/status 400))))
    (route/not-found "Invalid"))
   api-defaults))

(deftest test-set-webhook
  (let [stopper (s/run-server mock-telegram {:port 8180})]
    (try (let [res (set-webhook "http://localhost:8180" "example")]
           (is (= 200 (:status res))
               "Must receive HTTP OK on proper Content-Type")
           (is (= {"url" "example" "allowed_updates" ["message"]}
                  (json/parse-string (:body res)))
               "Must include webhook url in request body"))
         (finally (stopper)))))

(defn- mock-update [id text]
  (json/generate-string
   {"update_id" 1000
    "message"
    {"date" 1441645532
     "chat" {"last_name"  "TestLast"
             "id"         id
             "type"       "private"
             "first_name" "TestFirst"
             "username"   "TestUser"}
     "message_id" 1365
     "from" {"last_name"  "TestLast"
             "id"         id
             "first_name" "TestFirst"
             "username"   "TestUser"}
     "text" text}}))

(deftest test-parser
  (let [parse-command #(-> (mock-update 123 %)
                           (json/parse-string)
                           (parse-update)
                           (:command))]
    (testing "valid commands"
      (are [command text] (= command (parse-command text))
        :start  "/start"
        :help   "/help"
        :valid  "/valid"))
    (testing "invalid commands"
      (are [text] (= nil (parse-command text))
        "/INVALID"
        "/also invalid"
        "very_invalid"
        "/1234"))))

(deftest test-processor
  (testing "available commands"
    (is (= (str "Hello! I'm Zimbra Mailbot.\n"
                "I can forward emails from your "
                "Zimbra mailbox to this chat. "
                "To log into your account, send "
                "/login command. Send /help to "
                "list all available commands.")
           (:reply (process-update {:chat 123 :command :start}))))

    (is (= (str "I can understand these commands:\n"
                "/login - get link to log into my service\n"
                "/logout - log out of my service\n"
                "/help - display this help message again")
           (:reply (process-update {:chat 345 :command :help}))))

    (let [chat 567]
      (is (= "Logged in successfully!"
             (:reply (process-update {:chat chat :command :login}))))
      (is (= "You're logged in already."
             (:reply (process-update {:chat chat :command :login}))))
      (swap! sessions dissoc chat))

    (let [chat 789]
      (is (= "You're logged out already."
             (:reply (process-update {:chat chat :command :logout}))))
      (swap! sessions assoc chat {})
      (is (= "Logged out successfully!"
             (:reply (process-update {:chat chat :command :logout})))))))

(deftest test-send-message
  (let [stopper (s/run-server mock-telegram {:port 8180})]
    (try (let [res (send-message "http://localhost:8180"
                                 {:chat 123 :reply "Smalltalk"})]
           (is (= 200 (:status res))
               "Must receive HTTP OK on proper Content-Type")
           (is (= {"chat_id" 123 "text" "Smalltalk"}
                  (json/parse-string (:body res)))
               "Must include chat_id and text in request body"))
         (finally (stopper)))))

(deftest test-updates-route
  (let [resp-for
        #(-> (mock/request :post "/updates")
             (mock/body (mock-update 777 "Sample text"))
             (as-> req
                 (if %1 (mock/header req "X-Forwarded-For" %1) req))
             (handler))]
    (testing "authorized Telegram IPs"
      (are [ip] (= 200 (:status (resp-for ip)))
        "149.154.160.0"
        "149.154.175.255"
        "91.108.4.0"
        "91.108.7.255")
      (testing "request timeout"
        (with-redefs [zimbramailbot.app/updates-chan (y/chan)]
          (let [resp (resp-for "91.108.4.100")]
            (is (= 503 (:status resp)))
            (is (= "60" (get-in resp [:headers "Retry-After"])))))))
    (testing "other IPs"
      (are [ip] (= 404 (:status (resp-for ip)))
        "104.91.50.11"
        "2001:db8:85a3:8d3:1319:8a2e:370:7348"
        nil))))
