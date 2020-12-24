(ns browser.budget
  (:require [browser.utils :refer [get-category-value]]
            [goog.string :refer [format]]
            [re-frame.core :as rf]))

(def fixed-time {"Dormir" 8
                 "Ejercicio" 0.75
                 "Cuidado Personal" 0.5
                 "Comidas" 2
                 "Cuidado hogar" 0.5
                 "Jaiki" 0.75})


(do
  (defn total-percentage
    ([categories] (total-percentage categories nil))
    ([categories [year month]]
     (->> categories vals
          (map (fn [cat]
                 (:percentage (get-category-value [year month] cat))))
          (apply +))))

  #_(total-percentage categories))

(defn monthly-budget [categories spent-time-by-cat month-free-time [year month]]
  [:div [:h1 "Budget/month"]
   (map (fn [[cat data]]
          (let [cat-data (get-category-value [year month] data)
                sched-time (spent-time-by-cat cat 0)
                total-time (/ (* month-free-time (cat-data :percentage)) 100)
                left (- total-time sched-time)
                left-% (.toFixed (* 100 (/ left total-time)) 2)]
            [:p {:key cat}
             (format "%s %s%, %s hrs, scheduled: %s, left: %shrs (%s%)"
                     cat (cat-data :percentage)
                     total-time sched-time
                     left left-%)]))
        categories)])

(defn main []
  (let [free-time (- 24 (apply + (vals fixed-time)))
        month-free-time (* 30 free-time)
        cats @(rf/subscribe [:categories])
        acts @(rf/subscribe [:activities])
        year  @(rf/subscribe [:year])
        month @(rf/subscribe [:month])
        acts-by-cat (->> (get-in acts [year month])
                         vals
                         (apply merge)
                         vals
                         (group-by :cat))
        spent-time-by-cat (into {} (map (fn [[cat acts]]
                                          [cat (apply + (map :time acts))])
                                        acts-by-cat))
        scheduled-time (->> spent-time-by-cat vals (apply +))
        unscheduled-time (- month-free-time scheduled-time)]
    [:div
     [:div [:h1 "Fixed"] (map (fn [[cat %]] [:p {:key cat} cat " " %]) fixed-time)]
     [:div [:h1 "Scheduled time"]
      [:p (format "%s, %s%"
                  scheduled-time
                  (.toFixed (* 100 (/ scheduled-time month-free-time)) 2))]]
     [:div [:h1 "Free time"]
      [:p (format "%s/day, %s/month, %shrs unscheduled"
                  free-time month-free-time unscheduled-time)]]
     (monthly-budget cats spent-time-by-cat month-free-time [year month])]))
