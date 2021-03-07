(ns browser.budget
  (:require [browser.graphs :as graphs]
            [browser.utils :as utils]
            [browser.views.categories :as categories]
            [re-frame.core :as rf]
            [react-bootstrap :as rb]))

(defn budget-row
  [{:keys [cat cat-color sched-time total-time left left-%]}]
  [:tr {:key cat}
   [:td [:span {:key cat
                :class "d-flex align-items-center"} [:span {:class "mr-2"}
                (utils/render-dot cat-color 20)]
         cat]]
   [:td (utils/format-float total-time)]
   [:td (utils/format-float sched-time)]
   [:td (utils/format-float left)]
   [:td (utils/percentage-string left-%)]])

(defn monthly-budget [categories spent-time-by-cat month-free-time]
  (let [cats-data
        (map (fn [[cat data]]
               (let [cat-data (categories/get-category-value data)
                     cat-color (categories/get-category-color cat)
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
                                                 :left 0
                                                 :left-% 0}
                          cats-data)
                  (assoc :cat [:b "Total"] :cat-color {"r" 255 "g" 255 "b" 255 "a" 1})
                  (#(assoc % :left-% (if (zero? (:total-time %)) 0
                                         (* 100 (/ (:left % 0) (:total-time %)))))))
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


(defn get-tooltip-labels
  [tooltip-item data]
  (get-in (js->clj data) ["datasets" 0 "tooltipLabels" (.-index tooltip-item)]))

(defn main []
  (let [fixed-time @(rf/subscribe [:fixed-time])
        {:keys [free-time month-free-time]} @(rf/subscribe [:free-time])
        cats @(rf/subscribe [::categories/current-month-categories])
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
                                        acts-by-cat))]
    [:div
     (accordion
      0
      [["Charts"
        [:div
         (graphs/bars ""
                      @(rf/subscribe [::categories/monthly-categories-graph-data])
                      :chart-height 65
                      :options {:tooltips
                                {:callbacks
                                 {:label get-tooltip-labels}}})
         (graphs/bars "" @(rf/subscribe [:monthly-activities-graph-data])
                      :options {:tooltips
                                {:callbacks
                                 {:label get-tooltip-labels}}})]]
       ["Fixed and free time"
        (fixed-and-free-time cat fixed-time free-time month-free-time)]
       ["Monthly Budget"
        (monthly-budget cats spent-time-by-cat month-free-time)]])]))
