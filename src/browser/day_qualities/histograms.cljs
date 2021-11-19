(ns browser.day-qualities.histograms
  (:require [browser.db :as db]
            [browser.utils :as utils :refer [format-float get-category-value]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.set :as set]
            [browser.day-qualities.state :refer [is-valid? is-incomplete?]]
            [browser.graphs :as graphs]))


(def colors {:energy-level {"r" 255 "g" 227 "b" 88}
             :productivity-level {"r" 12 "g" 210 "b" 255}
             :creativity-level {"r" 255 "g" 12 "b" 211}
             :stress-level {"r" 175 "g" 0 "b" 0}
             :mood-level {"r" 255 "g" 255 "b" 255}
             :sleep-hours {"r" 110 "g" 0 "b" 255}
             :sleep-quality {"r" 182 "g" 138 "b" 241}})

(defn- get-quality-columns [qualities-of-month]
  (apply map (fn [& quality-in-month] quality-in-month)
         qualities-of-month))

(defn- month-histogram-data [[[year month] days-in-month day-qualities] _]
  (let [days (range 1 (inc days-in-month))
        qualities-by-day (group-by :day day-qualities)
        qualities [:energy-level :productivity-level :creativity-level
                   :stress-level :mood-level :sleep-hours :sleep-quality]
        qualities-data (get-quality-columns
                        (map #(->> % qualities-by-day
                                   first
                                   ((apply juxt qualities))
                                   (map (fn [value] (or value 0))))
                             days))
        tooltip-labels (map #(utils/fmt-day-date year month %) days)
        datasets (map (fn [label data]
                        {:label (name label)
                         :data (vec data)
                         :cubicInterpolationMode "monotone"
                         :tension 0.4
                         :tooltipLabels (map #(utils/fmt-str
                                               "%s, level: %s" %1 %2)
                                             tooltip-labels data)
                         :backgroundColor (utils/get-color-string (colors label))
                         :borderColor (utils/get-color-string (colors label))})
                      qualities qualities-data)]
    {:labels days
     :datasets datasets}))

(comment
  (month-histogram [@(rf/subscribe [:year-month])
                    @(rf/subscribe [:days-in-month])
                    @(rf/subscribe [:day-qualities/get-current-month])]
                   nil))

(rf/reg-sub :day-qualities-histograms/month
            :<- [:year-month]
            :<- [:days-in-month]
            :<- [:day-qualities/get-current-month]
            month-histogram-data)

(comment @(rf/subscribe [:day-qualities-histograms/month])
         @(rf/subscribe [:month-activities-histogram-graph-data]))
(defn month-histogram []
  (let [data @(rf/subscribe [:day-qualities-histograms/month
                             #_:month-activities-histogram-graph-data])]
    (graphs/line ""
                 data
                 :chart-height 100
                 :y-scale-type "linear"
                 :y-max (max (data :y-max) 12)
                 :x-title {:display true :text "Day"}
                 :y-title {:display true :text "Level"}
                 :options {:plugins
                           {:tooltip
                            {:callbacks
                             {:label utils/get-tooltip-labels}}}})))
