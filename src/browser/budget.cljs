(ns browser.budget
  (:require [re-frame.core :as rf]))
(def fixed-time {"Dormir" 8
                 "Ejercicio" 0.75
                 "Cuidado Personal" 0.5
                 "Comidas" 2
                 "Cuidado hogar" 0.5
                 "Jaiki" 0.75})
(defn main []
  (let [free-time (- 24 (apply + (vals fixed-time)))
        month-free-time (* 30 free-time)
        cats {"Trabajo" 30
              "Musica" 30
              "Techxploration" 15
              "Otros" 15
              "Libre" 10}
        total (apply + (vals cats))
        acts @(rf/subscribe [:activities])
        year  @(rf/subscribe [:year])
        month @(rf/subscribe [:month])
        acts-by-cat (->> (get-in acts [year month]) vals (group-by :cat))
        spent-time-by-cat (into {} (map (fn [[cat acts]] [cat (apply + (map :time acts))]) acts-by-cat))
]
    [:div
     [:div [:h1 "Fixed"] (map (fn [[cat %]] [:p {:key cat} cat " " %]) fixed-time)]
     [:div [:h1 "Free time"] [:p free-time "/day, " month-free-time "/month "]]
     [:div [:h1 "Budget/month"]
      (map (fn [[cat %]]
             (let [sched-time (spent-time-by-cat cat 0)
                   total-time (/ (* month-free-time %) 100)
                   left (- total-time sched-time)
                   left-% (.toFixed (* 100 (/ left total-time)) 2)]
               [:p {:key cat} cat " " % "%, "
                total-time " hrs, scheduled: " sched-time
                ", left: " left "hrs (" left-% "%)"]) ) cats)]
     [:p "Total: " total "%"]]))
