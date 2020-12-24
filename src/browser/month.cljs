(ns browser.month
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [browser.utils :refer [get-category-value]]
            ["date-fns" :as d]))


(do
  (defn days [month year]
    (mapv (fn [day] {:ymd [year month day]
                    :year year
                    :month month
                    :day day
                    :name  (d/format (js/Date. year month day) "MMM d")})
          (range 1 (inc (d/getDaysInMonth (js/Date. year month )))))))


(def create-activity (r/atom nil))
(defonce new-activity (r/atom {}))

(defn render-activities [activities]
  (map
   (fn [{:keys [cat act time id] :as activity}]
     [:div {:key id}
      [:p cat act time
       [:button {:on-click #(rf/dispatch [:delete-activity activity])} "-"]]])
   activities))

(defn render-create-activity-form [year month day day-name]
  (let [categories-data @(rf/subscribe [:categories])
        categories (->> categories-data keys sort)
        activities (->> categories-data
                        (map (fn [[cat data]]
                               [cat (:activities (get-category-value [year month] data))]))
                        (into {}))
        create? (= @create-activity day-name)]
    (println  activities)
    (when create?
      [:div
       [:label "Category"
        [:select
         {:value (@new-activity :cat "")
          :on-change
          (fn [ev]
            (swap! new-activity assoc :cat (-> ev .-target .-value)))}
         (cons [:option {:key "---"} "---"]
               (map (fn [cat] [:option {:key cat :value cat} cat]) categories))]]
       [:label "Activities"
        [:select
         {:value (@new-activity :act "")
          :on-change
          (fn [ev]
            (swap! new-activity assoc :act (-> ev .-target .-value)))}
         (cons [:option {:key "---"} "---"]
               (map (fn [[act]] [:option {:key act :value act} act]) (activities (@new-activity :cat ""))))]]
       [:label "Time"
        [:input
         {:type "number"
          :value (@new-activity :time)
          :on-change
          (fn [ev]
            (swap! new-activity assoc :time (-> ev .-target .-value js/Number)))}]]
       [:button
        {:on-click
         (fn []
           (rf/dispatch [:create-activity
                         (merge @new-activity
                                {:year year
                                 :month month
                                 :day day
                                 :id (str (random-uuid))})])
           (reset! new-activity {}))}
        "Create Activity"]])))

(defn main []
  (let [activities @(rf/subscribe [:activities])
        year @(rf/subscribe [:year])
        month @(rf/subscribe [:month])
        dates (days month year)]
    [:div
     [:h1 "Calendar"]
     [:div
      (doall
       (map (fn [{:keys [name day]}]
              (let [create? (= @create-activity name)]
                [:div {:key name}
                 [:h2 name
                  [:button
                   {:on-click (fn [] (swap! create-activity #(if (= % name) nil name)))}
                   (if create? "-" "+")]]
                 (render-create-activity-form year month day name)
                 (render-activities (vals (get-in activities [year month day])))]))
            dates))]]))
