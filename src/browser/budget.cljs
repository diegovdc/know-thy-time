(ns browser.budget
  (:require [browser.utils :refer [get-category-value]]
            [goog.string :refer [format]]
            [re-frame.core :as rf]
            [browser.utils :as utils]
            [react-bootstrap :as rb]
            [clojure.string :as str]
            [goog.string :as gstr]))


(defn budget-row
  [{:keys [cat cat-color sched-time total-time left left-%]}]
  [:tr {:key cat}
   [:td [:span {:key cat
                :class "d-flex align-items-center"} [:span {:class "mr-2"}
                (utils/render-dot cat-color 20)]
         cat]]
   [:td total-time]
   [:td sched-time]
   [:td left]
   [:td (utils/percentage-string left-%)]])

(defn monthly-budget [categories spent-time-by-cat month-free-time [year month]]
  (let [cats-data
        (map (fn [[cat data]]
               (let [cat-data (get-category-value [year month] data)
                     cat-color (cat-data :color)
                     sched-time (spent-time-by-cat cat 0)
                     total-time (/ (* month-free-time (cat-data :percentage)) 100)
                     left (- total-time sched-time)
                     left-% (* 100 (/ left total-time))]
                 {:cat cat
                  :cat-color cat-color
                  :sched-time sched-time
                  :total-time total-time
                  :left left
                  :left-% left-%}))
             categories)
        total (-> (reduce #(merge-with + %1 %2) {:total-time 0
                                                 :sched-time 0
                                                 :left 0}
                          cats-data)
                  (assoc :cat [:b "Total"] :cat-color {"r" 255 "g" 255 "b" 255 "a" 1})
                  (#(assoc % :left-% (* 100 (/ (:left % 0) (:total-time % 1))))))
        unit (fn [unit*] [:div {:class "text-center"} [:small unit*]])
        th (fn [text unit*] [:th [:div {:class "text-center"} text
                                 (when unit* (unit unit*))]])]

    [:div [:h1 "Monthly Budget"]
     [:> rb/Table
      [:thead [:tr
               (th "Category" nil)
               (th "Total" "hrs")
               (th "Scheduled" "hrs")
               (th "Left" "hrs")
               (th "Left" "%")]]
      [:tbody
       (map budget-row cats-data)
       (budget-row total)]]]))


(defn accordion [default-key cards]
  [:> rb/Accordion {:defaultActiveKey (str default-key)}
   (map-indexed (fn [i [title component]]
                  [:> rb/Card {:key i}
                   [:> rb/Card.Header
                    [:> rb/Accordion.Toggle
                     {:eventKey (str i) :as rb/Button :variant "link"}
                     title]]
                   [:> rb/Accordion.Collapse {:eventKey (str i)}
                    [:> rb/Card.Body component]]])
                cards)])

(defn fixed-and-free-time
  [cat fixed-time free-time month-free-time]
  (let [total-fixed (apply + (vals fixed-time))
        total-fixed-month (* 30 total-fixed)]
    [:div {:class "d-flex"}
     [:div [:h4 "Fixed"]
      [:p {:class "mb-0"} [:b [:u total-fixed]] " hrs/day"]
      [:p {:class "mb-2"} [:b [:u total-fixed-month]] " hrs/month"]
      (map (fn [[cat %]] [:p {:key cat :class "mb-0"} cat " " [:b [:u %]] " hrs/day"])
           fixed-time)]
     [:div {:class "ml-4"}
      [:h4 "Free time"]
      [:p {:class "mb-0"} [:b [:u free-time]] " hrs/day"]
      [:p {:class "mb-0"} [:b [:u month-free-time]] " hrs/month"]]]))

(defn main []
  (let [fixed-time @(rf/subscribe [:fixed-time])
        free-time (- 24 (apply + (vals fixed-time)))
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
        scheduled-time (->> spent-time-by-cat vals (apply +))]
    [:div
     (accordion 1 [["Fixed and free time"
                    (fixed-and-free-time cat fixed-time free-time month-free-time)]
                   ["Monthly Budget"
                    (monthly-budget cats spent-time-by-cat month-free-time [year month])]])]))
