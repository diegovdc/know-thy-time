(ns browser.month
  (:require [re-frame.core :as rf]
            ["date-fns" :as d]))


(do
  (defn days [month year]
    (map (fn [day] {:ymd [year month day]
                   :year year
                   :month month
                   :day day
                   :name  (d/format (js/Date. year month day) "MMM d")})
         (range 1 (inc (d/getDaysInMonth (js/Date. year month )))))))



(defn main []
  (let [activities @(rf/subscribe [:activities])
        year @(rf/subscribe [:year])
        month @(rf/subscribe [:month])
        dates (days month year)]
    (println activities)
    [:div
     [:h1 "Calendar"]
     [:div
      (map (fn [{:keys [name day]}]
             (let [{:keys [cat act time]} (get-in activities [year month day])]
               [:div {:key name} [:h2 name]
                [:p cat act time]
                ]))
           dates)]])
  )
