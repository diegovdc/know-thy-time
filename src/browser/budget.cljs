(ns browser.budget
  (:require
   [browser.graphs :as graphs]
   [browser.utils :as utils]
   [browser.views.categories :as categories]
   [browser.day-qualities.histograms :as dq-histograms]
   [date-fns :as d]
   [re-frame.core :as rf]
   [react-bootstrap :as rb]
   [browser.day-qualities.modal :as dq]))

(rf/reg-sub
 ;; From 0-100
 ::elapsed-percentage-of-month
 :<- [:days-in-month]
 :<- [:year-month]
 (fn [[days-in-month [year month]] _]
   (let [today (js/Date.)]
     (cond
       ;; Current month
       (and (= year (d/getYear today))
            (= month (d/getMonth today)))
       (* 100 (/ (d/getDate today) days-in-month))
       ;; In a future year
       (> year (d/getYear today)) 0
       ;; Some month in the past
       (> (d/getMonth today) month) 100
       ;; Some month in the future
       (< (d/getMonth today) month) 0
       ;; who knows?
       :default 0))))

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
               (let [cat-data data
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
     [:div [:small "Note: Also counts events marked as \"todo\""]]
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
        {:keys [free-time month-free-time]} @(rf/subscribe [:free-time])
        cats @(rf/subscribe [:month-categories])
        acts @(rf/subscribe [:activities])
        year  @(rf/subscribe [:year])
        month @(rf/subscribe [:month])
        %-of-month  @(rf/subscribe [::elapsed-percentage-of-month])
        cats-graph-data  @(rf/subscribe [::categories/monthly-categories-graph-data])
        cats-histogram  @(rf/subscribe [:month-activities-histogram-graph-data])
        acts-graph-data @(rf/subscribe [:monthly-activities-graph-data])
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
      [[[:p "Charts "
         [:small {:style {:color "white"}}
          (utils/fmt-str "(%s elapsed)"
                         (utils/percentage-string %-of-month))]]
        [:div
         (graphs/line ""
                      cats-histogram
                      :chart-height 100
                      :y-scale-type "linear"
                      :y-max (max (cats-histogram :y-max) 100)
                      :x-title (cats-histogram :x-title)
                      :y-title (cats-histogram :y-title)
                      :options {:plugins
                                {:tooltip
                                 {:callbacks
                                  {:label utils/get-tooltip-labels}}}})
         (graphs/bars ""
                      acts-graph-data
                      :x-title (acts-graph-data :x-title)
                      :y-title (acts-graph-data :y-title)
                      :options {:plugins
                                {:tooltip
                                 {:callbacks
                                  {:label utils/get-tooltip-labels}}}})
         (graphs/bars ""
                      cats-graph-data
                      :chart-height 65
                      :x-title (cats-graph-data :x-title)
                      :y-title (cats-graph-data :y-title)
                      :options {:plugins
                                {:tooltip
                                 {:callbacks
                                  {:label  utils/get-tooltip-labels}}}})

         (dq-histograms/month-histogram)]]
       ["Fixed and free time"
        (fixed-and-free-time cat fixed-time free-time month-free-time)]
       ["Monthly Budget"
        (monthly-budget cats spent-time-by-cat month-free-time)]])]))
