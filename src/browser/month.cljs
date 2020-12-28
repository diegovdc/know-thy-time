(ns browser.month
  (:require ["date-fns" :as d]
            ["react-bootstrap" :as rb]
            [browser.utils :as utils :refer [get-category-value]]
            [goog.string :as gstr]
            [goog.string.format]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.spec.alpha :as s]
            [browser.db :as db]))

(defn days [month year]
  (mapv (fn [day] {:ymd [year month day]
                  :year year
                  :month month
                  :day day
                  :name  (d/format (js/Date. year month day) "iii d")})
        (range 1 (inc (d/getDaysInMonth (js/Date. year month ))))))

(defonce create-activity (r/atom nil))
(defonce new-activity (r/atom {}))

(defn render-activities [activities]
  (doall
   (map
    (fn [{:keys [cat act time description id] :as activity}]
      (let [category (-> @(rf/subscribe [:categories]) (get cat) :default)]
        (when category
          [:div {:key id}
           [:div {:class "d-flex"}
            [:span {:style {:position "relative" :top 3}}
             (utils/render-dot (:color category) 16)]

            [:div {:class "ml-2"}
             [:p {:class "mb-0"}
              (gstr/format "%s %shr%s" act time (if (= time 1) "" "s"))

              [:span {:class "ml-2"}
               (utils/delete-btn #(rf/dispatch [:delete-activity activity]))]]

             [:p {:style {:max-width 200}}
              [:small cat (when description (str ": " description))]]]]])))
    activities)))

(defn daily-activity-data [activities]
  (let [activity-time (->>  activities
                            (group-by :cat)
                            (map (fn [[cat data]]
                                   (when cat
                                     [cat (apply + (map :time data))])))
                            (filter first)
                            (sort-by second))
        cat-colors @(rf/subscribe [:categories-colors])
        total-activity-time  (apply + (map second activity-time))
        total-free-time (- 24 total-activity-time)
        fixed-time @(rf/subscribe [:fixed-time])
        available-time (->> (apply + (vals fixed-time)) (- total-free-time))]
    [:div
     [:p {:class (str "mb-1" (when (> 0 available-time) " text-danger "))}
      "Available time: " available-time]
     (when (> total-free-time 0)
       [:div {:class "mb-3 d-flex align-items-center"}
        (map (fn [[cat time]]
               [:span {:key cat :class "d-inline-block position-relative mr-2"}
                [:span {:class "absolute-centered"} time]
                (utils/render-dot (get cat-colors cat "#fff") (+ 20 (* 5 time)) )])
             activity-time)
        (when (> total-activity-time 0) [:span {:class "display-4"} "= " total-activity-time ])])]))

(defn render-create-activity-form [year month day day-name]
  (let [categories-data @(rf/subscribe [:categories])
        categories (->> categories-data keys sort)
        activities (->> categories-data
                        (map (fn [[cat data]]
                               [cat (:activities
                                     (get-category-value [year month] data))]))
                        (into {}))
        create? (= @create-activity day-name)

        categories-options
        (cons [:option {:key "---"}
               "Choose a category"]
              (map (fn [cat] [:option {:key cat :value cat} cat])
                   categories))

        acts-for-cat (activities (@new-activity :cat []))
        activities-options (cons [:option {:key "---"}
                                  "Choose an activity"]
                                 (map (fn [[act]]
                                        [:option {:key act :value act} act])
                                      acts-for-cat))
        acts-for-cat? (seq acts-for-cat)

        ;; Handlers
        on-cat-change #(swap! new-activity assoc
                              :cat (utils/get-input-string %) :act "")
        on-act-change #(swap! new-activity assoc
                              :act (utils/get-input-string %))
        on-time-change #(swap! new-activity assoc
                               :time (utils/get-input-number %))
        on-description-change #(swap! new-activity assoc
                                      :description (utils/get-input-string %))
        ;; results
        created-activity (merge @new-activity
                                {:year year
                                 :month month
                                 :day day
                                 :id (str (random-uuid))})]
    (when create?
      (js/console.debug "validation" (s/explain-str ::db/day-activity created-activity))
      [:> rb/Form {:class "form-width"}
       (utils/select "Category" (@new-activity :cat "")
                     on-cat-change categories-options)
       (cond
         (and (-> @new-activity :cat empty? not) (not acts-for-cat?))
         [:p {:class "text-warning"}
          "Please register at least one activity for this category"]
         acts-for-cat?
         [:div
          (utils/select "Activity" (@new-activity :act "") on-act-change
                        activities-options)
          (utils/input "Duration" (@new-activity :time 0) on-time-change
                       :type "number")
          (utils/input "Description" (@new-activity :description "")
                       on-description-change)]
         :default nil)


       (utils/submit-btn
        "Add"
        (fn []
          (rf/dispatch [:create-activity created-activity])
          (reset! new-activity {}))
        :disabled (not (s/valid? ::db/day-activity created-activity)))])))

(defn main []
  (r/create-class
   {:component-did-mount
    (fn [this]
      (js/setTimeout
       #(let [day-elem (js/document.getElementById (str "day-" (d/getDate (js/Date.))))]
          (.scrollIntoView day-elem))
       100)
      (println "component did mount" (d/getDate (js/Date.))))
    :reagent-render
    (fn []
      (let [activities @(rf/subscribe [:activities])
            year @(rf/subscribe [:year])
            month @(rf/subscribe [:month])
            dates (days month year)]
        (when (and year month)
          [:div
           [:h1 (d/format (js/Date. year month ) "MMM Y")]
           [:div
            (doall
             (map (fn [{:keys [name day]}]
                    (let [create? (= @create-activity name)
                          activities (vals (get-in activities [year month day]))]
                      [:div {:key name}
                       [:h2 {:id (str "day-" day)} name
                        [:span {:class "ml-2"}
                         [:> rb/Button
                          {:variant "outline-light"
                           :on-click (fn [] (swap! create-activity #(if (= % name) nil name)))}
                          (if create? "-" "New activity")]]]
                       (render-create-activity-form year month day name)
                       [:div
                        (daily-activity-data activities)
                        (render-activities activities)]]))
                  dates))]])))}))
