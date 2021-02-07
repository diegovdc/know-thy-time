(ns browser.main
  (:require [browser.budget :as budget]
            [browser.db :as db]
            [browser.db.init :as db-init]
            [browser.month :as month]
            [browser.router :as router]
            [browser.utils :as utils]
            [browser.views.categories
             :as
             categories
             :refer
             [ensure-category-configs-exist-in-month]]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [re-frame.core :as rf]))

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

(def categories
  {"Trabajo" {:default {:percentage 30
                        :activities {"Fundamentally" {:hrs 80}}}}
   "Musica" {:default {:percentage 30
                       :activities {"Taller Abierto" {:hrs 30}}}}
   "Techxploration" {:default {:percentage 15
                               :activities {"Budgetime" {:hrs 30}}}}
   "Otros" {:default {:percentage 15
                      :activities {"Inversiones" {:hrs 8}}}}
   "Libre" {:default {:percentage 10}}})


;; -- Domino 2 - Event Handlers -----------------------------------------------

(rf/reg-event-db ;; sets up initial application state
 :initialize     ;; usage:  (dispatch [:initialize])
 (fn [_ _] ;; the two parameters are not important here, so use _
   (db/initialize-db)))    ;; so the application state will initially be a map with two keys

(rf/reg-event-db ;; usage:  (dispatch [:time-color-change 34562])
 :time-color-change ;; dispatched when the user enters a new colour into the UI text field
 (fn [db [_ new-color-value]] ;; -db event handlers given 2 parameters:  current application state and event (a vector)
   (assoc db :time-color new-color-value)))   ;; compute and return the new application state


(rf/reg-event-db                 ;; usage:  (dispatch [:timer a-js-Date])
 :timer                         ;; every second an event of this kind will be dispatched
 (fn [db [_ new-time]]          ;; note how the 2nd parameter is destructured to obtain the data value
   (assoc db :time new-time)))

(rf/reg-event-db
 :router
 (fn [db [_ router]]
   (assoc db :router router)))

(rf/reg-event-db :set-year (fn [db [_ year]] (assoc db :year year)))
(rf/reg-event-db :set-month (fn [db [_ year]] (assoc db :month year)))

(rf/reg-event-fx
 :restore-backup
 (fn [{:keys [db]} [_ backup]]
   {:db (merge db backup)
    :fx [[:save-categories]
         [:save-activities]
         [:save-fixed-time]]}))

;; TODO use update category to instanciate a new category config for each month
;; Look for previous config if not and use that as a basis for the new config
;; else use the default config



(rf/reg-event-fx
 :create-category
 (fn [{:keys [db]} [_ category-name]]

   {:db (let [new-cat {:percentage 0
                       :color {"r" (rand-int 255)
                               "g" (rand-int 255)
                               "b" (rand-int 255)
                               "a" 1}
                       :activities {}}
              year (:year db)
              month (:month db)]
          (cond
            (empty? category-name)
            (assoc db :alert
                   {:variant "danger"
                    :msg "The category must have a name"})

            (-> db :categories (get category-name))
            (assoc db :alert
                   {:variant "danger"
                    :msg "The category already exists"})

            :else
            (-> db
                ensure-category-configs-exist-in-month
                (assoc-in [:categories category-name :default] new-cat)
                (assoc-in [:categories category-name [year month]] new-cat))))
    :fx (if (empty? category-name) []
            [[:save-categories]])}))
(ensure-category-configs-exist-in-month
 {:categories {"cat-a" {:default {:a :b}
                        [2020 12] {}
                        [2021 2] {:c :d}}}
  :year 2020
  :month 1})

(rf/reg-event-fx
 :update-category
 (fn [{:keys [db]} [_ values-path value]]
   {:db (-> db
            ensure-category-configs-exist-in-month
            (assoc-in (concat [:categories] values-path) value))
    :fx [[:save-categories]]}))

(defn remove-activities-of-category [activities category-name]
  (walk/postwalk
   (fn [x]
     (if (and (s/valid? ::db/day-map x))
       (->> x
            (map (fn [[id day]] (if (= category-name (:cat day)) nil [id day])))
            (remove nil?)
            (into {}))
       x))
   activities))

(rf/reg-event-fx
 :delete-category
 (fn [{:keys [db]} [_ category-name]]
   {:db (-> db (update :categories dissoc category-name)
            (update :activities remove-activities-of-category category-name))
    :fx [[:save-categories]
         [:save-activities]]}))

(rf/reg-event-fx
 :create-category-activity
 (fn [{:keys [db]} [_ values-path value]]
   (println value)
   {:db (cond
          (empty? (last values-path))
          (assoc db :alert
                 {:variant "danger"
                  :msg "The activity  must have a name"})

          (-> db (get-in (concat [:categories] values-path)))
          (assoc db :alert
                 {:variant "danger"
                  :msg "The activity already exists"})

          :else (assoc-in db (concat [:categories] values-path) value))
    :fx [[:save-categories]]}))

(rf/reg-event-fx
 :delete-category-activity
 (fn [{:keys [db]} [_ values-path activity-name]]
   {:db (update-in db (concat [:categories] values-path) dissoc activity-name)
    :fx [[:save-categories]]}))

;; Daily activities
(rf/reg-event-fx
 :create-activity
 (fn [{:keys [db]} [_ {:keys [year month day id] :as activity}]]
   {:db (assoc-in db [:activities year month day id] activity)
    :fx [[:save-activities]]}))

(rf/reg-event-fx
 :delete-activity
 (fn [{:keys [db]} [_ {:keys [year month day id]}]]
   {:db (update-in db [:activities year month day] dissoc id)
    :fx [[:save-activities]]}))

(rf/reg-event-fx
 :create-fixed-time
 (fn [{:keys [db]} [_ fixed-cat]]
   {:db (assoc-in db [:fixed-time fixed-cat] 0)
    :fx [[:save-fixed-time]]}))

(rf/reg-event-fx
 :update-fixed-time
 (fn [{:keys [db]} [_ fixed-cat value]]
   {:db (assoc-in db [:fixed-time fixed-cat] value)
    :fx [[:save-fixed-time]]}))

(rf/reg-event-fx
 :delete-fixed-time
 (fn [{:keys [db]} [_ fixed-cat]]
   {:db (update db :fixed-time dissoc fixed-cat)
    :fx [[:save-fixed-time]]}))

(rf/reg-event-fx
 :close-alert

 (fn [{:keys [db]} [_]]
   {:db (assoc db :alert db-init/initial-alert)}))

(rf/reg-fx
 :save-categories
 (fn []
   (js/localStorage.setItem "categories" (pr-str @(rf/subscribe [:categories])))))

(rf/reg-fx
 :save-activities
 (fn []
   (js/localStorage.setItem "activities" (pr-str @(rf/subscribe [:activities])))))

(rf/reg-fx
 :save-fixed-time
 (fn []
   (js/localStorage.setItem "fixed-time" (pr-str @(rf/subscribe [:fixed-time])))))


;; -- Domino 4 - Query  -------------------------------------------------------
(rf/reg-sub :db (fn  [db _] db))

(rf/reg-sub
 :backup
 (fn [{:keys [version activities categories fixed-time]} _]
   (pr-str {:version version
            :activities activities
            :categories categories
            :fixed-time fixed-time})))

(rf/reg-sub
 :backup-json
 (fn [{:keys [version activities categories fixed-time]} _]
   (js/JSON.stringify (clj->js {:version version
                                :activities activities
                                :categories categories
                                :fixed-time fixed-time}))))

(comment
  (:year @(rf/subscribe [:db]))
  @(rf/subscribe [:backup])
  @(rf/subscribe [:backup-json]))

(rf/reg-sub
 :time
 (fn [db _] ;; db is current app state. 2nd unused param is query vector
   (:time db)))

(rf/reg-sub :alert (fn [db _] (:alert db)))
(rf/reg-sub :fixed-time (fn [db _] (:fixed-time db)))
(rf/reg-sub :free-time (fn [db _] (let [fixed-time (:fixed-time db)
                                       free-time (- 24 (apply + (vals fixed-time)))
                                       month-free-time (* 30 free-time)]
                                   {:free-time free-time
                                    :month-free-time month-free-time})))
(rf/reg-sub :categories (fn [db _] (:categories db)))
(rf/reg-sub :activities (fn [db _] (:activities db)))
(rf/reg-sub :month (fn [db _] (:month db)))
(rf/reg-sub :year (fn [db _] (:year db)))
(rf/reg-sub :year-month
            :<- [:year]
            :<- [:month]
            (fn [[year month] _] [year month]))

(rf/reg-sub :categories-colors
            (fn [db _] (->> (:categories db)
                           (map (fn [[cat data]]
                                  [cat (->> data :default :color)]))
                           (into {}))))

(rf/reg-sub :activities-of-month
            :<- [:activities]
            :<- [:year]
            :<- [:month]
            (fn [[activities year month] _]
              (-> activities (get-in [year month]) vals)))

(rf/reg-sub :month-categories
            :<- [::categories/current-month-categories]
            :<- [::categories/current-configured-month]
            (fn [[categories year-month] _]
              (->> categories (map (fn [[cat val]] [cat (get val year-month)])) (into {}))))
(rf/reg-sub
 :time-by-activities-of-month-by-cat
 :<- [:activities-of-month]
 :<- [:month-categories]
 (fn [[activities-of-month month-categories] _]
   (let [activity-exercised-%
         (fn [cat [act act-data]]
           (let [act-budget (get-in month-categories [cat :activities act :hrs])
                 act-used-time (->> act-data (map :time) (apply +))]
             [act (* 100 (/ act-used-time act-budget))]))

         add-missing-activities
         (fn [cat exercised-acts]
           (->> (set (map first exercised-acts))
                (set/difference (-> month-categories
                                    (get cat)
                                    :activities keys
                                    set))
                (map (fn [act] [act 0]))
                (into exercised-acts)))]

     (->> activities-of-month
          (mapcat vals)
          (group-by :cat)
          (map (fn [[cat acts]]
                 [cat (->> acts
                           (group-by :act)
                           (map (partial activity-exercised-% cat))
                           (add-missing-activities cat)
                           (sort-by second)
                           reverse)]))
          (sort-by (comp (partial apply +) (partial map second) second))
          reverse))))



(rf/reg-sub
 :monthly-activities-graph-data
 :<- [::categories/current-month-categories]
 :<- [::categories/current-configured-month]
 :<- [:time-by-activities-of-month-by-cat]
 (fn [[categories year-month acts-time-by-cat] _]
   (let [cats (->> categories (map (fn [[cat val]] [cat (get val year-month)])) (into {}))
         _ (println "acts" cats)
         labels (->> acts-time-by-cat (mapcat second) (map first))
         data (->> acts-time-by-cat (mapcat second) (map (comp #(.toFixed % 2) second)))

         colors-with-repeats
         (->> acts-time-by-cat
              (map (juxt (comp count second)
                         (comp :color (partial get cats) first))))

         background-colors
         (mapcat (fn [[n color]]
                   (repeat n (utils/get-color-string
                              (assoc color "a" 0.3))))
                 colors-with-repeats)

         border-colors (mapcat (fn [[n color]]
                                 (repeat n (utils/get-color-string color)))
                               colors-with-repeats)]

     {:labels labels
      :datasets [{:label "Exercised activities %"
                  :data data
                  :backgroundColor background-colors
                  :borderColor border-colors
                  :borderWidth 1}]})))


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
(defn focus-current-month [ev]
  (when (and (.-altKey ev) (= 77 (.-keyCode ev)))
    (.focus (js/document.getElementById "Current month"))))


(defn init []
  (rf/clear-subscription-cache!)
  (rf/dispatch-sync [:initialize])
  (router/start-app!)
  (js/window.addEventListener "keyup" focus-current-month))

(defn ^:dev/after-load clear-cache-and-render!
  []
  ;; The `:dev/after-load` metadata causes this function to be called
  ;; after shadow-cljs hot-reloads code. We force a UI update by clearing
  ;; the Reframe subscription cache.
  (rf/clear-subscription-cache!)
  (js/window.removeEventListener "keyup" focus-current-month)
  (init))

(defn dev-re-init []
  (rf/clear-subscription-cache!)
  (rf/dispatch-sync [:initialize])
  (init))

(comment
  @(rf/subscribe [:activities])
  (update-in @(rf/subscribe [:activities]) [2021 0 1] dissoc "1ea5d382-e9d4-4d6e-ab1a-4cb53a705d7d")
  (js/localStorage.setItem "activities" (pr-str {}))
  (js/localStorage.setItem "activities"
                           (pr-str {2021 {0 {1 {"1-1" {:cat "Musica"
                                                       :act "Tocar"
                                                       :time 1
                                                       :id "1-1"
                                                       :year 2021
                                                       :month 0
                                                       :day 1}}}}}))
  (reader/read-string (js/localStorage.getItem "activities")))
