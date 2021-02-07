(ns browser.month
  (:require ["date-fns" :as d]
            ["react-bootstrap" :as rb]
            [browser.db :as db]
            [browser.utils :as utils :refer [format-float get-category-value]]
            [browser.views.categories :as categories]
            [clojure.spec.alpha :as s]
            [goog.string :as gstr]
            goog.string.format
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn days [month year]
  (mapv (fn [day] {:ymd [year month day]
                  :year year
                  :month month
                  :day day
                  :name  (d/format (js/Date. year month day) "iii d")})
        (range 1 (inc (d/getDaysInMonth (js/Date. year month ))))))

(defonce create-activity (r/atom nil))
(defonce new-activity (r/atom {}))

(defonce show-activities (r/atom {}))
(defn render-activities [day-name activities]
  [:div {:class "d-flex month__activity"}
   (if-not (-> @show-activities (get day-name))
     [:button {:class "btn month__activity_toggle_show"
               :on-click #(swap! show-activities assoc day-name true)}
      [:u (gstr/format "Show activities (%s)" (count activities))]]
     [:div
      (doall
       (map
        (fn [{:keys [cat act time description id] :as activity}]
          (let [year-month @(rf/subscribe [::categories/current-configured-month])
                category (-> @(rf/subscribe [:categories]) (get-in [cat year-month]))]
            (when category
              [:div {:key id}
               [:div {:class "ml-2"}

                [:p {:class "mb-0"}
                 [:span {:class "mr-1"}
                  (utils/render-dot (:color category) 16)]
                 (gstr/format "%s %shr%s" act time (if (= time 1) "" "s"))

                 [:span {:class "ml-2 month__activity_delete"}
                  (utils/delete-btn #(rf/dispatch [:delete-activity activity]))]]

                [:p {:style {:max-width 200}}
                 [:small cat (when description (str ": " description))]]]])))
        activities))
      [:button {:class "btn" :on-click #(swap! show-activities dissoc day-name)}
       [:u "Hide activities"]]])])

(defn daily-activity-data [activities]
  (let [activity-time (->>  activities
                            (group-by :cat)
                            (map (fn [[cat data]]
                                   (when cat
                                     [cat (apply + (map :time data))])))
                            (filter first)
                            (sort-by second))
        cat-colors @(rf/subscribe [:categories-colors])
        total-activity-time (apply + (map second activity-time))
        total-free-time (- 24 total-activity-time)
        fixed-time @(rf/subscribe [:fixed-time])
        available-time (->> (apply + (vals fixed-time)) (- total-free-time))]
    [:div
     [:p {:class (str "mb-1" (when (> 0 available-time) " text-danger "))}
      "Available time: " (format-float available-time)]
     (when (> total-free-time 0)
       [:div {:class "mb-3 d-flex align-items-center"}
        (map (fn [[cat time]]
               (let [color (get cat-colors cat "#fff")
                     color-str (utils/get-color-string color)
                     style {:background-color (utils/get-color-string
                                               (assoc color "a" 0.3))
                            :border (str "3px solid " color-str)}]
                 [:span {:key cat :class "d-inline-block position-relative mr-2"}
                  [:span {:class "absolute-centered"
                          :style {:color color-str
                                  :font-size 18}}
                   [:b time]]
                  (utils/render-dot color
                                    (+ 42 (* 3  time))
                                    :style style)]))
             activity-time)
        (when (> total-activity-time 0)
          [:span {:style {:font-size 48}} "= " (format-float total-activity-time) ])])]))

(defn render-create-activity-form [year month day day-name]
  (let [year-month @(rf/subscribe [::categories/current-configured-month])
        categories-data @(rf/subscribe [:categories])
        categories (->> categories-data keys sort)
        activities (->> categories-data
                        (map (fn [[cat data]]
                               [cat (:activities
                                     (get-category-value year-month data))]))
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

         (js/console.debug "Activity is valid?"
                           (s/valid? ::db/day-activity created-activity)
                           (s/explain-str ::db/day-activity created-activity))
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
       #(let [day (d/getDate (js/Date.))
              day-elem (js/document.getElementById (str "day-" day))]
          (.scrollIntoView day-elem)
          (swap! show-activities assoc day true))
       100))
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
                       [:div
                        (daily-activity-data activities)
                        (render-create-activity-form year month day name)
                        (render-activities day activities)]]))
                  dates))]])))}))
