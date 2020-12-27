(ns browser.date-utils
  (:require [date-fns :as d]))

(defn next-month [year month]
  (let [next (d/add (js/Date. year month) (clj->js {:months 1}))]
    {:month (d/getMonth next)
     :year (d/getYear next) }))

(defn prev-month [year month]
  (let [next (d/sub (js/Date. year month) (clj->js {:months 1}))]
    {:month (d/getMonth next)
     :year (d/getYear next) }))
