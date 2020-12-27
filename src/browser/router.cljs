(ns browser.router
  (:require [browser.routes :as routes]
            [re-frame.core :as re-frame]
            [reagent.dom :as dom]
            [browser.date-utils :as dutils]
            [reitit.coercion.spec :as rss]
            [browser.alert :as alert]
            [reitit.core :as r]
            [reitit.frontend :as rf]
            [reitit.frontend.controllers :as rfc]
            [reitit.frontend.easy :as rfe]))

;;; Events ;;;

(re-frame/reg-event-db ::initialize-db
  (fn [db _]
    (if db
      db
      {:current-route nil})))

(re-frame/reg-event-fx ::push-state
  (fn [db [_ & route]]
    {:push-state route}))

(re-frame/reg-event-db ::navigated
  (fn [db [_ new-match]]
    (let [old-match   (:current-route db)
          controllers (rfc/apply-controllers (:controllers old-match) new-match)]
      (assoc db :current-route (assoc new-match :controllers controllers)))))

;;; Subscriptions ;;;

(re-frame/reg-sub ::current-route
  (fn [db]
    (:current-route db)))

;;; Effects ;;;

;; Triggering navigation from events.

(re-frame/reg-fx :push-state
  (fn [route]
    (apply rfe/push-state route)))

;;; Routes ;;;

(defn href
  "Return relative url for given route. Url can be used in HTML links."
  ([k]
   (href k nil nil))
  ([k params]
   (href k params nil))
  ([k params query]
   (rfe/href k params query)))


(defn on-navigate [new-match]
  (when new-match
    (re-frame/dispatch [::navigated new-match])))

(defn make-router [routes]
  (rf/router
    routes
    {:data {:coercion rss/coercion}}))

(defn init-routes! [routes]
  (let [router (make-router routes)]
    (js/console.log "initializing routes")
    (rfe/start!
     router
     on-navigate
     {:use-fragment true})
    router))


(defn nav [{:keys [router current-route]}]
  (let [year @(re-frame/subscribe [:year])
        month @(re-frame/subscribe [:month])
        routes
        (map (fn [{:keys [route-name text params]}]
               (let [route (r/match-by-name router route-name params)
                     text (or text (-> route :data :link-text))]
                 [:li {:key text}
                  (when (and (= route-name (-> current-route :data :name))
                             (not= route-name ::routes/calendar))

                    "> ")
                  ;; Create a normal links that user can click
                  [:a {:href (href route-name params)} text]]))
             [{:route-name ::routes/calendar
               :text "Previous month"
               :params (dutils/prev-month year month)}
              {:route-name ::routes/home}
              {:route-name ::routes/calendar
               :text "Next month"
               :params (dutils/next-month year month)}
              {:route-name ::routes/categories}
              {:route-name ::routes/fixed-time}])
        ]
    [:ul {:class "d-flex justify-content-around"} routes]))

(defn router-component [{:keys [router]}]
  (let [current-route @(re-frame/subscribe [::current-route])]
    [:div
     (alert/main)
     [nav {:router router :current-route current-route}]
     (when current-route
       [(-> current-route :data :view)])]))

(defn start-app! []
  (let [router (init-routes! routes/routes)] ;; Reset routes on figwheel reload
    (re-frame/dispatch [:router router])
    (dom/render [router-component {:router router}]
                (.getElementById js/document "app"))))

;;; Setup ;;;

#_(def debug? ^boolean goog.DEBUG)

#_(defn dev-setup []
  (when true
    (enable-console-print!)
    (println "dev mode")))
