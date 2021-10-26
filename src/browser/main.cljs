(ns browser.main
  (:require [browser.budget :as budget]
            [browser.db :as db]
            [browser.db.init :as db-init]
            [browser.month :as month]
            [browser.router :as router]
            [browser.utils :as utils]
            [goog.string :as gstr]
            [goog.string.format]
            [browser.views.categories
             :as
             categories
             :refer
             [ensure-category-configs-exist-in-month]]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.walk :as walk]
            ["date-fns" :as d]
            [re-frame.core :as rf]))

;; A detailed walk-through of this source code is provided in the docs:
;; https://day8.github.io/re-frame/dominoes-live/

;; -- Domino 1 - Event Dispatch -----------------------------------------------



;; -- Domino 2 - Event Handlers -----------------------------------------------

(rf/reg-event-db ;; sets up initial application state
 :initialize     ;; usage:  (dispatch [:initialize])
 (fn [_ _] ;; the two parameters are not important here, so use _
   (db/initialize-db)))    ;; so the application state will initially be a map with two keys


(rf/reg-event-db
 :router
 (fn [db [_ router]]
   (assoc db :router router)))

(comment
  (-> @(rf/subscribe [:db]) :categories (get "Self-kindness"))
  )
(rf/reg-event-db :hide-privacy-wall (fn [db _] (assoc db :show-privacy-wall? false)))

(rf/reg-event-db :set-year (fn [db [_ year]] (assoc db :year year)))

(rf/reg-event-db :set-month (fn [db [_ year]] (assoc db :month year)))

(rf/reg-event-fx
 :restore-backup
 (fn [{:keys [db]} [_ backup]]
   {:db (merge db backup)
    :fx [[:save-categories]
         [:save-activities]
         [:save-fixed-time]]}))

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

(rf/reg-event-fx
 :update-category-color
 (fn [{:keys [db]} [_ category color]]
   (println category color)
   {:db (let [cat (-> db :categories (get category)
                      (->> (map (fn [[k data]]
                                  [k (assoc data :color color)]))
                           (into {})))]
          (assoc-in db [:categories category] cat))
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
   {:db (cond
          (empty? (last values-path))
          (assoc db :alert
                 {:variant "danger"
                  :msg "The activity  must have a name"})

          (-> db (get-in (concat [:categories] values-path)))
          (assoc db :alert
                 {:variant "danger"
                  :msg "The activity already exists"})

          :else (-> db
                    ensure-category-configs-exist-in-month
                    (assoc-in (concat [:categories] values-path) value)))
    :fx [[:save-categories]]}))

(rf/reg-event-fx
 :delete-category-activity
 (fn [{:keys [db]} [_ values-path activity-name]]
   {:db (update-in db (concat [:categories] values-path) dissoc activity-name)
    :fx [[:save-categories]]}))

;; Daily activities
(rf/reg-event-fx
 :create-activity
 (fn [{:keys [db]} [_ {:keys [year month day] :as activity}]]
   (let [id (str (random-uuid))]
     {:db (assoc-in db [:activities year month day id] (assoc activity :id id))
      :fx [[:save-activities]]})))

(rf/reg-event-fx
 :edit-activity
 (fn [{:keys [db]} [_ {:keys [year month day id original-day] :as activity}]]
   {:db (-> db
            (update-in [:activities year month original-day] dissoc id)
            (assoc-in  [:activities year month day id] (dissoc activity :original-day)))
    :fx [[:save-activities]]}))

(rf/reg-event-fx
 :delete-activity
 (fn [{:keys [db]} [_ {:keys [year month day id]}]]
   {:db (update-in db [:activities year month day] dissoc id)
    :fx [[:save-activities]]}))

(rf/reg-event-fx
 :mark-todo-as-done
 (fn [{:keys [db]} [_ {:keys [year month day id]}]]
   {:db (update-in db [:activities year month day id] assoc :todo? false)
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

(rf/reg-sub :days-in-month
            :<- [:year]
            :<- [:month]
            (fn [[year month] _] (d/getDaysInMonth (js/Date. year month))))

(rf/reg-sub :available-hours
            :<- [:fixed-time]
            (fn [fixed-time _]
              (- 24 (apply + (vals fixed-time)))))

(rf/reg-sub :available-hours-in-month
            :<- [:available-hours]
            :<- [:days-in-month]
            (fn [[fixed-time days-in-month] [_ query]]
              (println query)
              (* fixed-time days-in-month)))

(comment @(rf/subscribe [:days-in-month])
         @(rf/subscribe [:available-hours])
         @(rf/subscribe [:year-month])
         @(rf/subscribe [:activities-of-month [2021 3]])
         @(rf/subscribe [:available-hours-in-month]))

(rf/reg-sub :activities-of-month
            :<- [:activities]
            :<- [:year-month]
            (fn [[activities year-month] [_ year-month*]]
              (-> activities
                  (get-in (or year-month* year-month))
                  vals)))
(comment
  (-> @(rf/subscribe [:activities-of-month])))
(rf/reg-sub :month-categories
            :<- [::categories/current-month-categories]
            :<- [::categories/current-configured-month]
            (fn [[categories year-month] _]
              (->> categories
                   (map (fn [[cat val]] [cat (get val year-month)]))
                   (into {}))))

(rf/reg-sub :show-privacy-wall? (fn [db _] (:show-privacy-wall? db)))

(defn make-year-month-combos [years months]
  (mapcat (fn [y] (map (fn [m] [y m]) months))  years))

(defn make-year-month-range [[year-1 month-1] [year-2 month-2]]
  (let [inter-years (range (inc year-1) year-2)
        inter-years-combos (make-year-month-combos inter-years (range 0 12))
        year-1-months (if (= year-1 year-2)
                        (range month-1 (inc month-2))
                        (range month-1 12))
        year-2-months (if (= year-1 year-2) [] (range 0 (inc month-2)))
        year-1-combos (make-year-month-combos [year-1] year-1-months)
        year-2-combos (make-year-month-combos [year-2] year-2-months)
        range (->> (concat year-1-combos inter-years-combos year-2-combos)
                   (sort-by (juxt first second)))]
    range))

(rf/reg-sub :all-months-range
            :<- [:activities]
            (fn [acts]
              (let [initial-year (-> acts first first)
                    initial-month (-> acts first second first first)]
                (make-year-month-range
                 [initial-year
                  initial-month]
                 [(d/getYear (js/Date.))
                  (d/getMonth (js/Date.))]))))

(-> @(rf/subscribe [:activities])
    (get-in [2021 0])
    vals)
(defn spy [x] (println x) x)
(do
  (defn activities-time-in-range
    [activities [_ [year-1 month-1] [year-2 month-2]]]
    (let [range (make-year-month-range [year-1 month-1] [year-2 month-2])]
      (map (fn [year-month]
             [year-month
              (-> activities
                  (get-in year-month)
                  vals
                  (->> (mapcat vals)
                       (remove :todo?)
                       (group-by :act)
                       (mapcat (fn [[name acts]]
                                 {[(-> acts first :cat) name] (->> acts (map :time) (apply +))}))
                       (into {})))])
           range)))
  (activities-time-in-range @(rf/subscribe [:activities]) [nil [2021 0] [2024 2]]))
(rf/reg-sub :activities-time-in-range
            :<- [:activities]
            activities-time-in-range)

(rf/reg-sub :activity-names-by-category
            :<- [:categories]
            (fn [categories _]
              (->> categories
                   (map (fn [[cat data]]
                          [cat (-> data :default :activities keys sort)]))
                   (into {}))))


(defn get-newest-version-of-category
  [cat-data]
  (second (if (> (count cat-data) 1)
            (first (sort-by first > (dissoc cat-data :default)))
            (:default cat-data))))

(defn category-activity-pairs [categories _]
  (->> categories
       (mapcat (fn [[cat data]]
                 (->> data
                      get-newest-version-of-category
                      :activities keys sort
                      (map (fn [act] [cat act])))))))

(comment
  @(rf/subscribe [:categories])
  (category-activity-pairs @(rf/subscribe [:categories]) nil))

(rf/reg-sub :category-activity-pairs
            :<- [:categories]
            category-activity-pairs)

(defn activities-histogram
  [[activities category-activity-pairs categories-colors]
   [_ [year-1 month-1] [year-2 month-2] {:keys [acts-to-show-on-render]
                                         :or {acts-to-show-on-render 5} }]]
  (let [activities-time (activities-time-in-range
                         activities
                         [nil
                          [year-1 month-1]
                          [year-2 month-2]])
        labels (map (comp (partial apply utils/fmt-ym-date) first)
                    activities-time)
        datasets (->> activities-time
                      (mapcat (fn [[_ month-act-data]]
                                (map (fn [cat-act]
                                       [cat-act (get month-act-data cat-act 0)])
                                     category-activity-pairs)))
                      (group-by first)
                      (map (fn [[[cat act] acts]]
                             (let [data (map second acts)]
                               {:label (utils/fmt-str "%s (%s)" act cat)
                                :data data
                                :total-hrs (apply + data)
                                :borderColor (-> cat categories-colors
                                                 utils/get-color-string)
                                :backgroundColor (-> cat categories-colors
                                                     (assoc "a" 0.2)
                                                     utils/get-color-string)
                                })))
                      (sort-by (comp (partial * -1) :total-hrs))
                      (map-indexed
                       (fn [i el]
                         (assoc el :hidden (> i acts-to-show-on-render)))))]
    (js/console.log datasets)
    {:labels labels
     :datasets datasets}))

(comment
  @(rf/subscribe [:activities])
  @(rf/subscribe [:category-activity-pairs])
  (activities-histogram [@(rf/subscribe [:activities])
                         @(rf/subscribe [:category-activity-pairs])
                         @(rf/subscribe [:categories-colors])
                         ]
                        [nil [2021 0] [2021 5]] ))
(rf/reg-sub :activities-histogram
            :<- [:activities]
            :<- [:category-activity-pairs]
            :<- [:categories-colors]
            activities-histogram)


(comment
  @(rf/subscribe [:activities-in-range [2021 0] [2024 2]]))

(rf/reg-sub
 ;; Completed percentage of time for each activity grouped by category
 :time-by-activities-of-month-by-cat
 :<- [:activities-of-month]
 :<- [:month-categories]
 (fn [[activities-of-month month-categories] _]
   (let [activity-exercised-%
         (fn [cat [act act-data]]
           (let [act-budget (get-in month-categories [cat :activities act :hrs])
                 act-used-time (->> act-data (remove :todo?) (map :time) (apply +))]
             [act (if (or (nil? act-budget) (zero? act-budget))
                    0
                    (* 100 (/ act-used-time act-budget)))]))

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
 :total-hours-per-activity-of-month
 :<- [:activities-of-month]
 (fn [activities-of-month _]
   (->> activities-of-month
        (mapcat vals)
        (group-by (juxt :cat :act))
        (map (fn [[k activities]] [k (->> activities (remove :todo?) (map :time) (apply +))]))
        (into {}))))

(comment
  @(rf/subscribe [::categories/monthly-categories-graph-data])
  @(rf/subscribe [:activities-of-month])
  @(rf/subscribe [:month-categories])
  @(rf/subscribe [::categories/current-month-categories])
  @(rf/subscribe [:time-by-activities-of-month-by-cat]))

(rf/reg-sub
 :monthly-activities-graph-data
 :<- [:month-categories]
 :<- [:time-by-activities-of-month-by-cat]
 :<- [:total-hours-per-activity-of-month]
 (fn [[cats acts-time-by-cat hours-per-activity] _]
   (let [labels (->> acts-time-by-cat (mapcat second) (map first))
         data (->> acts-time-by-cat (mapcat second) (map (comp #(.toFixed % 2) second)))

         colors-with-repeats
         (->> acts-time-by-cat
              (map (juxt (comp count second)
                         (comp :color (partial get cats) first))))

         tooltip-labels
         (->> acts-time-by-cat
              (mapcat (fn [[cat-name acts]]
                        (map (fn [[act percentage]]
                               (let [total-hours (get hours-per-activity
                                                      [cat-name act] 0)
                                     estimated-hours (get-in
                                                      cats
                                                      [cat-name :activities act :hrs]
                                                      0)]
                                 (gstr/format "%s/%s hrs (%s)"
                                              (utils/format-float total-hours)
                                              (utils/format-float estimated-hours)
                                              (utils/percentage-string percentage))))
                             acts))))

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
                  :tooltipLabels tooltip-labels
                  :borderColor border-colors
                  :borderWidth 1}]})))

(do
  (defn accumulate-time [acts-by-day days-in-month]
    (reduce (fn [acc day]
              (conj acc (->> (acts-by-day day [])
                             (map :time)
                             (apply + (or (last acc) 0)))))
            []
            (range 1 (inc days-in-month))))

  (defn month-activities-histogram
    [[acts-of-month days-in-month categories-colors month-category-hours] _]
    (println "MMMMMMMMM" month-category-hours)
    (let [time-by-cat (->> acts-of-month
                           (mapcat vals)
                           (group-by :cat)
                           (map (fn [[cat acts]]
                                  (let [acts-by-day (group-by :day acts)
                                        time (accumulate-time acts-by-day days-in-month)]
                                    [cat time]))))
          labels (range 1 (inc days-in-month))
          y-max (apply max (mapcat second time-by-cat))
          datasets (map (fn [[cat hrs]]
                          (let [data (map (fn [hrs] (* 100 (/ hrs (:estimated-hours (month-category-hours cat))))) hrs)]
                            {:label cat
                             :data hrs ;; data
                             ;; FIXME tooltip doesn't seem to be showing the correct data
                             :tooltipLabels (map (fn [perc hrs]
                                                   (utils/fmt-str "%s% (%shrs)"
                                                                  (utils/format-float perc)
                                                                  (utils/format-float hrs)))
                                                 data hrs)
                             :borderColor (-> cat categories-colors
                                              utils/get-color-string)
                             :backgroundColor (-> cat categories-colors
                                                  (assoc "a" 0.2)
                                                  utils/get-color-string)}))
                        time-by-cat)]
      {:labels labels
       :datasets datasets
       :y-max y-max})))

(comment
  (month-activities-histogram [@(rf/subscribe [:activities-of-month])
                               @(rf/subscribe [:days-in-month])
                               @(rf/subscribe [:categories-colors])
                               @(rf/subscribe [::categories/month-category-hours])]
                              nil))


(rf/reg-sub :month-activities-histogram-graph-data
            :<- [:activities-of-month]
            :<- [:days-in-month]
            :<- [:categories-colors]
            :<- [::categories/month-category-hours]
            month-activities-histogram)
;; -- Entry Point -------------------------------------------------------------
(defn focus-current-month
  "alt+m to focus the current month menu item"
  [ev]
  (when (and (.-altKey ev) (= 77 (.-keyCode ev)))
    (.focus (js/document.getElementById "Current month"))))


(defn db-and-edn-data-have-diverged?
  "Returns `true` if the current in-memory state of the window has diverged from
  the backed-up in-disk edn data"
  []
  (-> @(rf/subscribe [:db])
      (select-keys [:fixed-time :categories :activities])
      (not= {:fixed-time (db-init/get-fixed-time)
             :categories (db-init/get-categories)
             :activities (db-init/get-activities)})))

(defn reload-data-from-backup
  "Used to reinitialize the app so that data is kept in sync between different windows"
  [_ev]
  (when (db-and-edn-data-have-diverged?)
    (rf/dispatch [:initialize])
    (router/start-app!)))

(defn hide-privacy-wall [ev]
  (when @(rf/subscribe [:show-privacy-wall?])
    (rf/dispatch [:hide-privacy-wall])))

(defn init []
  (rf/clear-subscription-cache!)
  (rf/dispatch-sync [:initialize])
  (router/start-app!)
  (js/window.addEventListener "keyup" focus-current-month)
  (js/window.addEventListener "keyup"  hide-privacy-wall)
  (js/window.addEventListener "click"  hide-privacy-wall)
  ;; Reinitialize database on window focus, so that different tabs are kept in sync
  (js/window.addEventListener "focus" reload-data-from-backup))


(comment
  (rf/dispatch-sync [:initialize]))
(defn ^:dev/after-load clear-cache-and-render!
  []
  ;; The `:dev/after-load` metadata causes this function to be called
  ;; after shadow-cljs hot-reloads code. We force a UI qupdate by clearing
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
  (js/localStorage.setItem "categories" (pr-str {}))
  (js/localStorage.setItem "activities"
                           (pr-str {2021 {0 {1 {"1-1" {:cat "Musica"
                                                       :act "Tocar"
                                                       :time 1
                                                       :id "1-1"
                                                       :year 2021
                                                       :month 0
                                                       :day 1}}}}}))
  (reader/read-string (js/localStorage.getItem "activities")))
