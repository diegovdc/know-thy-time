(ns browser.routes
  (:require [re-frame.core :as rf]
            [browser.views.main :as views]
            [browser.views.categories :as categories]
            [browser.views.fixed-time :as fixed-time]
            ["date-fns" :as d]))



;;; Views ;;;


(defn home-page []
  [:div
   [:h1 "This is home page"]
   [:button
    ;; Dispatch navigate event that triggers a (side)effect.
    {:on-click #(rf/dispatch [::push-state ::sub-page2])}
    "Go to sub-page 2"]])

(defn sub-page1 []
  [:div
   [:h1 "This is sub-page 1"]])

(defn sub-page2 []
  [:div
   [:h1 "This is sub-page 2"]])


(def routes
  ["/"
   [""
    {:name      ::home
     :view      views/main
     :link-text "Current month"
     :controllers
     [{ ;; Do whatever initialization needed for home page
       ;; I.e (re-frame/dispatch [::events/load-something-with-ajax])
       :start (fn [& params]
                (let [today (js/Date.)]
                  (rf/dispatch [:set-year (d/getYear today)])
                  (rf/dispatch [:set-month (d/getMonth today)]))
                (js/console.log "Entering home page"))
       ;; Teardown can be done here.
       :stop  (fn [& params] (js/console.log "Leaving home page"))}]}]
   ["categories"
    {:name      ::categories
     :view      categories/main
     :link-text "Categories"
     :controllers
     [{:start (fn [& params] (js/console.log "Entering sub-page 1"))
       :stop  (fn [& params] (js/console.log "Leaving sub-page 1"))}]}]
   ["fixed-time"
    {:name      ::fixed-time
     :view      fixed-time/main
     :link-text "Fixed time"
     :controllers
     [{:start (fn [& params] (js/console.log "Entering sub-page 1"))
       :stop  (fn [& params] (js/console.log "Leaving sub-page 1"))}]}]
   ["calendar/:year/:month"
    {:name      ::calendar
     :view      views/main
     :link-text "Calendar"
     :controllers
     [{:parameters {:path [:year :month]}
       :start (fn [& params]
                (rf/dispatch [:set-year (-> params first :path :year int)])
                (rf/dispatch [:set-month (-> params first :path :month int)]))
       :stop  (fn [& params] (js/console.log "Leaving sub-page 1"))}]}]])
