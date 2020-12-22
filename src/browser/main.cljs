(ns browser.main
  (:require [reagent.dom :as dom]
            [re-frame.core :as rf]
            [kee-frame.core :as k]
            [clojure.string :as str]
            [browser.month :as month]
            [browser.budget :as budget]
            [cljs.reader :as reader]))

(defn get-activities []
  (reader/read-string (js/localStorage.getItem "activities")))

;; A detailed walk-through of this source code is provided in the docs:
;; https://day8.github.io/re-frame/dominoes-live/

;; -- Domino 1 - Event Dispatch -----------------------------------------------

(defn dispatch-timer-event
  []
  (let [now (js/Date.)]
    (rf/dispatch [:timer now])))  ;; <-- dispatch used

;; Call the dispatching function every second.
;; `defonce` is like `def` but it ensures only one instance is ever
;; created in the face of figwheel hot-reloading of this file.
(defonce do-timer (js/setInterval dispatch-timer-event 1000))


;; -- Domino 2 - Event Handlers -----------------------------------------------

(rf/reg-event-db              ;; sets up initial application state
 :initialize                 ;; usage:  (dispatch [:initialize])
 (fn [_ _]                   ;; the two parameters are not important here, so use _
   {:activities (get-activities)
    :year 2021
    :month 0
    :time (js/Date.) ;; What it returns becomes the new application state
    :time-color "#abc"}))    ;; so the application state will initially be a map with two keys


(rf/reg-event-db                ;; usage:  (dispatch [:time-color-change 34562])
 :time-color-change            ;; dispatched when the user enters a new colour into the UI text field
 (fn [db [_ new-color-value]]  ;; -db event handlers given 2 parameters:  current application state and event (a vector)
   (assoc db :time-color new-color-value)))   ;; compute and return the new application state


(rf/reg-event-db                 ;; usage:  (dispatch [:timer a-js-Date])
 :timer                         ;; every second an event of this kind will be dispatched
 (fn [db [_ new-time]]          ;; note how the 2nd parameter is destructured to obtain the data value
   (assoc db :time new-time)))  ;; compute and return the new application state


;; -- Domino 4 - Query  -------------------------------------------------------


(rf/reg-sub
 :time
 (fn [db _] ;; db is current app state. 2nd unused param is query vector
   (:time db)))

(rf/reg-sub :activities (fn [db _] (:activities db)))
(rf/reg-sub :month (fn [db _] (:month db)))
(rf/reg-sub :year (fn [db _] (:year db)))

(rf/reg-sub
 :time-color
 (fn [db _]
   (:time-color db)))


;; -- Domino 5 - View Functions ----------------------------------------------

(defn clock
  []
  [:div.example-clock
   {:style {:color @(rf/subscribe [:time-color])}}
   (-> @(rf/subscribe [:time])
       .toTimeString
       (str/split " ")
       first)])

(defn color-input
  []
  [:div.color-input
   "Time color: "
   [:input {:type "text"
            :value @(rf/subscribe [:time-color])
            :on-change #(rf/dispatch [:time-color-change (-> % .-target .-value)])}]])  ;; <---

(defn ui
  []
  [:div
   #_[:h1 "Hello world, it is now"]
   #_[clock]
   #_[color-input]
   (budget/main)
   (month/main)])

;; -- Entry Point -------------------------------------------------------------

(defn render
  []
  (reagent.dom/render [ui]
                      (js/document.getElementById "app")))

(defn init
  []
  (rf/dispatch-sync [:initialize]) ;; put a value into application state
  (render)                         ;; mount the application's ui into '<div id="app" />'
  )

(defn ^:dev/after-load clear-cache-and-render!
  []
  ;; The `:dev/after-load` metadata causes this function to be called
  ;; after shadow-cljs hot-reloads code. We force a UI update by clearing
  ;; the Reframe subscription cache.
  (rf/clear-subscription-cache!)
  (init)
  (render))

(comment
  (js/localStorage.setItem "activities" (pr-str {2021 {0 {1 {:cat "Musica" :act "Tocar" :time 1 }}}}))
  (reader/read-string (js/localStorage.getItem "activities")))
