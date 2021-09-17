(ns browser.views.categories
  (:require ["react-bootstrap" :as rb]
            ["react-color" :as color]
            [browser.graphs :as graphs]
            [browser.utils :as utils]
            [clojure.string :as str]
            [date-fns :as d]
            [goog.string :as gstr]
            goog.string.format
            [re-frame.core :as rf]
            [react-bootstrap-icons :as icons]
            [reagent.core :as r]))


(defn get-cat-config-dates [categories] (-> (first categories) second keys))
(defn get-previous-configured-month
  "Look for the closest previous month registered in categories.
  If the current-year/month hasbeen configured, then it returns that one
  Assumes all categories have at least a `:default` and if alrheady updated a
  shared latest `[year month]` config. Therefore it only needs to pick the first
  category to find whe want"
  [categories [current-year current-month]]
  (->> (get-cat-config-dates categories)
       (remove #(= :default %))
       (sort-by (juxt first second) >)
       (drop-while (fn [[year month]]
                     ;; Remove more recent dates than the current year/month
                     (and (>= year current-year)
                          (> month current-month))))
       first
       (#(or % :default))))

(comment (get-previous-configured-month
          {"cat-a" {:default {}
                    [2020 12] {}
                    [2021 2] {}}}
          [2021 2]))

(rf/reg-sub
 ::current-configured-month
 (fn [{:keys [categories year month]} _]
   (get-previous-configured-month categories [year month])))

(comment
  @(rf/subscribe [::current-configured-month]))

(defn maybe-create-year-month-categories-configs
  "NOTE that `previous-year-month`  may be a tuple `[year month]` or `:default`"
  [categories previous-year-month [current-year current-month]]
  (if ((set (get-cat-config-dates categories)) [current-year current-month])
    categories
    (reduce
     (fn [cats [cat-name configs]]
       (assoc-in cats [cat-name [current-year current-month]]
                 (get configs previous-year-month
                      ;; Adds `:default` in the case that for some buggy reason
                      ;; the previous-year-month does not exist in the configs.
                      (:default configs))))
     categories
     categories)))

(comment
  (maybe-create-year-month-categories-configs
   {"cat-a" {:default {:a :b}
             [2020 12] {}
             [2021 2] {:c :d}}}
   [2021 2]
   [2021 3]))

(defn ensure-category-configs-exist-in-month [db]
  "Takes the db and returns the db with categories with the updated configs"
  (let [categories (:categories db)
        current-year (:year db)
        current-month (:month db)
        prev-year-month (get-previous-configured-month
                         categories
                         [current-year current-month])
        updated-categories (maybe-create-year-month-categories-configs
                            categories
                            prev-year-month
                            [current-year current-month])]
    (assoc db :categories updated-categories)))

(defn cat-input
  ([data data-key val-name val-path]
   (cat-input data data-key val-name val-path utils/get-input-string "text"))
  ([data data-key val-name val-path get-input-val-fn type]
   (utils/input val-name
                (data-key data)
                #(rf/dispatch
                  [:update-category
                   val-path
                   (get-input-val-fn %)])
                :type type)))

(defn render-activity [category-name [activity-name data]]
  (let [year-month @(rf/subscribe [::current-configured-month])]
    [:div {:key (str category-name activity-name)
           :class "categories__activity mb-2 d-flex"}
     (cat-input data :hrs activity-name
                [category-name year-month :activities activity-name :hrs]
                utils/get-input-number
                "number")
     [:span {:class "align-self-center"} "hrs/month"]
     (utils/delete-btn #(rf/dispatch [:delete-category-activity
                                      [category-name year-month :activities] activity-name]))]))

(def new-activity (r/atom {}))
(defn create-new-activity [category-name]
  (let [new-act (get @new-activity category-name "")
        year-month @(rf/subscribe [:year-month])]
    [:div {:class "mw-300"}
     (utils/input-with-btn
      "New activity" "Create" new-act
      #(do (println (utils/get-input-string %))
           (swap! new-activity assoc category-name (utils/get-input-string %)))
      #(do (rf/dispatch [:create-category-activity
                         ;; TODO maybe the activity should exist in previous months?
                         [category-name year-month :activities new-act] {:hrs 0} ])
           (swap! new-activity dissoc category-name)))]))

(defn render-activities [category-name activities]
  [:div (doall (map (partial render-activity category-name) activities))
   (create-new-activity category-name)])

(def color-pickers (r/atom {}))
(defn toggle-color-picker [category]
  (swap! color-pickers #(assoc % category (not (get % category)))))

(let [color-atom (r/atom {})]
  (defn color-picker [cat-name color]
    [:> color/ChromePicker
     {:color (clj->js (get @color-atom cat-name color))
      :on-change #(reset! color-atom
                          {cat-name (-> % js->clj (get "rgb"))})
      :on-change-complete
      #(rf/dispatch
        [:update-category-color cat-name
         (-> % js->clj (get "rgb") )])}]))

(defn render-category [available-hours-month [cat-name cat-data]]
  (let [year-month @(rf/subscribe [::current-configured-month])
        data (utils/get-category-value year-month cat-data)
        total-hours (/ (* (data :percentage) available-hours-month) 100)
        projected-hours (->> data :activities vals (map :hrs) (apply +))
        color (data :color {:r (rand-int 255) :g (rand-int 255) :b (rand-int 255) :a 1})]
    [:div {:key cat-name :class "mb-4"}
     [:h2 {:class "categories__title"}
      (cat-input data
                 :percentage (str cat-name ": ")
                 [cat-name year-month :percentage]
                 utils/get-input-number
                 "number")
      [:span {:class "categories__percentage"} "%"]
      [:span {:on-click #(toggle-color-picker cat-name)}
       (utils/render-dot color 20)]
      (utils/delete-btn #(rf/dispatch [:delete-category cat-name]))]
     (when (@color-pickers cat-name)
       (color-picker cat-name color))
     [:p (gstr/format "Total hours: %s, still free hours: %s "
                      (utils/format-float total-hours)
                      (utils/format-float (- total-hours projected-hours)))]
     (render-activities cat-name (data :activities))

     ]))

(def new-category (r/atom ""))

(defn left-column [categories]
  (let [year-month @(rf/subscribe [::current-configured-month])
        fixed-time @(rf/subscribe [:fixed-time])
        available-hours (- 24 (apply + (vals fixed-time)))
        available-hours-month (* 30 available-hours)]
    [:div
     [:div {:class "mb-3 mw-300"}
      (utils/input-with-btn
       "New category" "Create" @new-category
       #(reset! new-category (utils/get-input-string %))
       (fn []
         (rf/dispatch [:create-category @new-category])
         (reset! new-category "")))]
     [:div {:class "mb-3"}
      [:div "Total allocated time: " (utils/categories-total-percentage
                                      categories
                                      year-month) "%"]
      [:div (gstr/format "Total available hours: %s/day, %s/month "
                         (utils/format-float available-hours)
                         (utils/format-float available-hours-month))]]
     (doall (map (partial render-category available-hours-month) categories))]))


(defn categories-data [categories]
  (let [year-month (get-previous-configured-month categories @(rf/subscribe [:year-month]))
        cat-data (->>  categories
                       (map (fn [[cat val]]
                              [cat (-> val (get year-month))]))
                       (sort-by (comp  :percentage second)))]
    {:labels (map first cat-data)
     :datasets [{:label "Allocated Time"
                 :data (map (comp :percentage second) cat-data)
                 :backgroundColor (map (comp utils/get-color-string
                                             :color
                                             second)
                                       cat-data)}]}))

(defn right-column [categories]
  [:div {:class "w-100"}
   [:div {:style {:position "sticky" :top 0}}
    (graphs/pie "" (categories-data categories)
                :options {:legend {:labels {:fontColor "white" :fontSize 20}}})]])

(defn title []
  (let [year-month @(rf/subscribe [::current-configured-month])
        [year month] (if (vector? year-month)
                       year-month
                       [@(rf/subscribe [:year]) @(rf/subscribe [:month])])
        date (d/format (js/Date. year month) "MMMM Y")]
    [:h1 (str "Categories " date)]))


(rf/reg-sub
 ;; Returns only the categories that are available on the currently selected month
 ::current-month-categories
 :<- [::current-configured-month]
 :<- [:categories]
 (fn [[year-month categories] _]
   (if (= year-month :default)
     categories
     (filter (fn [[_ data]]
               ((set (keys data)) year-month))
             categories))))

(defn main []
  (let [categories @(rf/subscribe [::current-month-categories])]
    [:div (title)
     [:div {:class "d-flex"}
      (left-column categories)
      (right-column categories)]]))

(defn get-category-value [category]
  (let [year-month @(rf/subscribe [::current-configured-month])]
    (get category year-month)))

(defn get-category-color [category]
  (-> @(rf/subscribe [:categories]) (get category) :default :color) )
(comment @(rf/subscribe [:categories]))

(rf/reg-sub
 ::monthly-categories-graph-data
 :<- [::current-month-categories]
 :<- [:activities]
 :<- [:year]
 :<- [:month]
 :<- [:free-time]
 :<- [::current-configured-month]
 (fn [[categories activities year month {:keys [month-free-time]}
       current-configured-month] _]
   (let [cats (->> categories
                   (map (fn [[cat val]] [cat (get val current-configured-month)]))
                   (into {}))
         acts (-> activities
                  (get-in [year month])
                  vals
                  (->> (mapcat vals)
                       (remove :todo?)
                       (group-by :cat)))
         cat-data (->> cats
                       (map (fn [[cat data]]
                              (let [estimated-hours (-> data :percentage
                                                        (/ 100) (* month-free-time))
                                    total-hours (apply + (map :time (get acts cat)))]
                                {:name cat
                                 :advance (* 100 (/ total-hours estimated-hours))
                                 :total-hours total-hours
                                 :estimated-hours estimated-hours})))
                       (sort-by :total-hours)
                       reverse)
         data (map (comp #(.toFixed % 2) :advance) cat-data)
         cat-names (map :name cat-data)
         background-colors (map #(-> cats (get %)
                                     :color
                                     (assoc "a" 0.3)
                                     utils/get-color-string)
                                cat-names)
         border-colors (map #(-> cats (get %)
                                 :color
                                 utils/get-color-string)
                            cat-names)
         tooltip-labels (map (fn [{:keys [total-hours estimated-hours]}]
                               (utils/fmt-str
                                "%s/%s hrs (%s%)"
                                (utils/format-float total-hours)
                                (utils/format-float estimated-hours)
                                (utils/format-float (* 100 (/ total-hours estimated-hours)))))
                             cat-data)]
     {:labels cat-names
      :datasets [{:label "Advanced %"
                  :data data
                  :backgroundColor background-colors
                  :borderColor border-colors
                  :tooltipLabels tooltip-labels
                  :borderWidth 1}]})))
