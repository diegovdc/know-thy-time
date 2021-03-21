(ns browser.db
  (:require [browser.db.init
             :refer
             [get-activities get-categories get-fixed-time initial-alert]]
            [browser.views.categories :as categories]
            [clojure.spec.alpha :as s]
            [date-fns :as d]
            [re-frame.core :as rf]))

(defn initialize-db [] {:version "0.0.1"
                        :current-route nil
                        :router nil
                        :show-privacy-wall? true
                        :activities (get-activities)
                        :categories (get-categories)
                        :fixed-time (get-fixed-time)
                        :alert initial-alert
                        :year (d/getYear (js/Date.))
                        :month (d/getMonth (js/Date.))
                        :time (js/Date.) ;; What it returns becomes the new application state
                        :time-color "#abc"})

(defn in-categories? [cat]
  ((set (keys @(rf/subscribe [:categories]))) cat))

(defn in-activities? [act]
  (let [year-month @(rf/subscribe [::categories/current-configured-month])
        acts (->> @(rf/subscribe [:categories])
                  vals
                  (mapcat (comp keys :activities #(get % year-month)))
                  set)]
    (acts act)))

(s/def ::cat (s/and string? #(in-categories? %)))
(s/def ::act (s/and string? #(in-activities? %)))
(s/def ::time (s/and number? #(> % 0)))
(s/def ::year number?)
(s/def ::month (s/and number? #(<= 0 % 11)))
(s/def ::day (s/and number? #(<= 1 % 31))) ; using days from 1 to 31
(s/def ::id string?)

(s/def ::day-activity (s/keys :req-un [::cat ::act ::time ::year ::month ::day ::id]))

(s/def ::day-map (s/map-of string? ::day-activity))
