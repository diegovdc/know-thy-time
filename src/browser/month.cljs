(ns browser.month
  (:require ["date-fns" :as d]
            ["react-bootstrap" :as rb]
            [browser.db :as db]
            [browser.day-qualities.modal :as dq]
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
(defonce edit-activity-modal-state (r/atom nil))
(defonce new-activity (r/atom {}))

(defonce show-activities (r/atom {}))

(defn todo-str [todo?] (if todo? "(todo)" ""))



(defn render-activities [day-name activities]
  [:div {:class "d-flex month__activity"}
   (if-not (-> @show-activities (get day-name))
     [:button {:class "btn month__activity_toggle_show"
               :on-click #(swap! show-activities assoc day-name true)}
      [:u (gstr/format "Show activities (%s)" (count activities))]]
     [:div
      (->> activities
           (map
            (fn [{:keys [cat act time description id todo?] :as activity}]
              (let [year-month @(rf/subscribe [::categories/current-configured-month])
                    category (-> @(rf/subscribe [:categories]) (get-in [cat year-month]))]
                (when category
                  [:div {:key id :class "month__activity_event"}
                   [:div {:class "ml-2"}

                    [:p {:class (str "mb-0 " (when todo? "text-info"))}
                     [:span {:class "mr-1"} (utils/render-dot (:color category) 16)]
                     (gstr/format "%s %s %shr%s" (todo-str todo?) act time (if (= time 1) "" "s"))

                     (when todo?
                       [:span {:class "ml-2 month__activity_mark-as-done"}
                        (utils/done-btn #(rf/dispatch [:mark-todo-as-done activity]))])

                     [:span {:class "ml-2 month__activity_delete"}
                      (utils/edit-btn #(reset! edit-activity-modal-state
                                               (assoc activity
                                                      :original-day (activity :day))))]

                     [:span {:class "ml-2 month__activity_delete"}
                      (utils/delete-btn #(rf/dispatch [:delete-activity activity]))]]

                    [:p {:style {:max-width 200}}
                     [:small cat (when description (str ": " description))]]]]))))
           doall)
      [:button {:class "btn" :on-click #(swap! show-activities dissoc day-name)}
       [:u "Hide activities"]]])])

(defn set-todos-category
  "Todo items will have the category set to \"Todo\""
  [activities]
  (map #(if (:todo? %) (assoc % :cat "Todo") %)
       activities))

(defn daily-activity-data [activities]
  (let [activity-time (->>  activities
                            set-todos-category
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
               (let [color (get cat-colors cat {"r" 255 "g" 255 "b" 255 "a" 0.3})
                     color-str (utils/get-color-string color)
                     style {:background-color (utils/get-color-string
                                               (assoc color "a" 0.3))
                            :border (str "3px solid " color-str)}]
                 [:span {:key cat
                         :class "d-inline-block position-relative mr-2"
                         :title cat}
                  [:span {:class "absolute-centered"
                          :style {:color color-str
                                  :font-size 18}}
                   [:b (utils/format-float time 1)]]
                  (utils/render-dot color
                                    (+ 40 (* 5 (js/Math.log (js/Math.pow 10 time))))
                                    :style style)]))
             activity-time)
        ;; TODO will probably remove this
        #_(when (> total-activity-time 0)
            [:span {:style {:font-size 48}} "= " (format-float total-activity-time) ])])]))

(defn render-activity-form [dispatch-action spec activity-atom day show?]
  (let [year-month @(rf/subscribe [::categories/current-configured-month])
        year @(rf/subscribe [:year])
        month @(rf/subscribe [:month])
        categories-data @(rf/subscribe [:categories])
        categories (->> categories-data keys sort)
        activities (->> categories-data
                        (map (fn [[cat data]]
                               [cat (:activities
                                     (get-category-value year-month data))]))
                        (into {}))

        categories-options
        (cons [:option {:key "---" :value ""}
               "Choose a category"]
              (map (fn [cat] [:option {:key cat :value cat} cat])
                   categories))

        acts-for-cat (sort-by first (activities (@activity-atom :cat [])))
        activities-options (cons [:option {:key "---" :value ""}
                                  "Choose an activity"]
                                 (map (fn [[act]]
                                        [:option {:key act :value act} act])
                                      acts-for-cat))
        acts-for-cat? (seq acts-for-cat)

        ;; Handlers
        on-cat-change #(swap! activity-atom assoc
                              :cat (utils/get-input-string %) :act "")
        on-act-change #(swap! activity-atom assoc
                              :act (utils/get-input-string %))
        on-time-change #(swap! activity-atom assoc
                               :time (utils/get-input-number %))
        on-description-change #(swap! activity-atom assoc
                                      :description (utils/get-input-string %))
        on-todo-change #(swap! activity-atom assoc :todo? %)
        ;; results
        resulting-activity (-> @activity-atom
                               (dissoc ::show?)
                               (merge {:year year
                                       :month month
                                       :day (or (@activity-atom :day)
                                                day)}))]
    (when show?
      (js/console.debug "validation" (s/explain-str spec resulting-activity))
      [:> rb/Form {:class "form-width"}
       (utils/select "Category" (@activity-atom :cat "")
                     on-cat-change categories-options)
       (cond
         (and (-> @activity-atom :cat empty? not) (not acts-for-cat?))
         [:p {:class "text-warning"}
          "Please register at least one activity for this category"]
         acts-for-cat?
         [:div
          (utils/select "Activity" (@activity-atom :act "") on-act-change
                        activities-options)
          (utils/input "Duration" (@activity-atom :time 0) on-time-change
                       :type "number")
          (utils/input "Description" (@activity-atom :description "")
                       on-description-change)
          (utils/checkbox "Todo?"
                          (@activity-atom :todo? false)
                          on-todo-change)]
         :default nil)

       (js/console.debug "Activity is valid?"
                         (s/valid? spec resulting-activity)
                         (s/explain-str spec resulting-activity))
       (utils/submit-btn
        (if (= dispatch-action :create-activity)"Add" "Update")
        (fn []
          (rf/dispatch [dispatch-action resulting-activity])
          (reset! activity-atom {}))
        :disabled (not (s/valid? spec resulting-activity)))])))


(defn close-modal [] (reset! edit-activity-modal-state {}))

(defn edit-activity-modal [dates]
  (utils/modal "Edit activity"
               (when (seq @edit-activity-modal-state)
                 [:div {:class "form-width"}
                  (utils/select "Date" (@edit-activity-modal-state :day)
                                #(swap! edit-activity-modal-state
                                        assoc
                                        :day (utils/get-input-number %))
                                (map (fn [{day-name :name  day :day}]
                                       [:option {:key day :value day} day-name])
                                     dates))
                  (render-activity-form :edit-activity
                                        ::db/day-activity
                                        edit-activity-modal-state
                                        10
                                        true)])
               (not (nil? (seq @edit-activity-modal-state)))
               close-modal))

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
           (edit-activity-modal dates)
           (dq/modal)
           [:div
            (doall
             (map (fn [{day-name :name  day :day}]
                    (let [create? (= @create-activity day-name)
                          activities (vals (get-in activities [year month day]))]
                      [:div {:key day-name}
                       [:h2 {:id (str "day-" day)} day-name
                        [:span {:class "ml-2"}
                         [:> rb/Button
                          {:variant "outline-light"
                           :on-click (fn [] (swap! create-activity #(if (= % day-name) nil day-name)))}
                          (if create? "-" "New activity")]]]
                       [:div
                        (dq/button year month day day-name)
                        (daily-activity-data activities)
                        (render-activity-form :create-activity
                                              ::db/day-activity-draft
                                              new-activity
                                              day
                                              (= @create-activity day-name) )
                        (render-activities day activities)]]))
                  dates))]])))}))
