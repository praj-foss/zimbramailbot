(ns chat.app-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [chat.app :refer :all]))

(deftest test-handler
  (testing "not-found route"
    (let [response (handler (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))
