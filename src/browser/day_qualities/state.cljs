(ns browser.day-qualities.state
  (:require [re-frame.core :as rf]
            [clojure.set :as set]
            [clojure.string :as str]))

(declare create-day-quality)

(rf/reg-sub :day-qualities/get-all
            (fn [db _] (db :day-qualities)))


(rf/reg-sub :day-qualities/get-day
            (fn [db [_ year month day day-name]]
              (get-in db [:day-qualities year month day]
                      {:year year
                       :month month
                       :day day
                       :day-name day-name})))

(rf/reg-sub :day-qualities/get-month
            (fn [db [_ year month]]
              (->> (get-in db [:day-qualities year month])
                   vals
                   (sort-by :day))))

(rf/reg-sub :day-qualities/get-current-month
            (fn [db _]
              (let [{:keys [year month]} db]
                (->> (get-in db [:day-qualities year month])
                     vals
                     (sort-by :day)))))

(rf/reg-event-fx :day-qualities/create-day #'create-day-quality)

(comment @(rf/subscribe [:day-qualities/get-all])
         @(rf/subscribe [:day-qualities/get-day 2021 9 28])
         @(rf/subscribe [:day-qualities/get-month 2021 9])
         @(rf/subscribe [:day-qualities/get-current-month]))



(defn is-valid? [{:keys [energy-level
                         mood-level
                         productivity-level
                         creativity-level
                         stress-level
                         states-of-being
                         sleep-hours
                         sleep-quality]}]
  (and
   (every? int? [energy-level
                 mood-level
                 productivity-level
                 creativity-level
                 stress-level
                 sleep-hours
                 sleep-quality])
   (seq states-of-being)
   (not (zero? states-of-being))))

(defn is-incomplete? [{:keys [energy-level
                              mood-level
                              productivity-level
                              creativity-level
                              stress-level
                              states-of-being
                              sleep-hours
                              sleep-quality]}]
  (let [validation (conj (map int? [energy-level
                                    mood-level
                                    productivity-level
                                    creativity-level
                                    stress-level
                                    sleep-hours
                                    sleep-quality])
                         (not (empty? states-of-being)))]
    (boolean
     (and (some true? validation)
          (not (every? true? validation))))))

(comment (is-valid?
          (get-in @(rf/subscribe [:day-qualities/get-all]) [2021 10 2]))
         (is-incomplete?
          (get-in @(rf/subscribe [:day-qualities/get-all]) [2021 10 3])))

(defn new-tag?
  "New tags do not have a UUID yet"
  [tag]
  (not (re-matches
        #"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        (tag :id))))

(comment (remove new-tag? [{:id "b146d915-3619-4ee6-915f-8317e8629898"}]))

(defn remove-duplicate-tags
  [states-of-being-options new-states-of-being]
  (let [opts-by-name (group-by (comp str/lower-case :name)
                               states-of-being-options)]
    {:new (remove (fn [state] (first
                              (opts-by-name (str/lower-case (:name state)))))
                  new-states-of-being)
     :possibly-missing
     (->> new-states-of-being
          (map (fn [state] (first (opts-by-name (str/lower-case (:name state))))))
          (remove nil?)
          set
          vec)}))


(comment (remove-duplicate-tags
          [{:id  "b146d915-3619-4ee6-915f-8317e8629898" :name "Hola"}]

          [{:id "tag-hola" :name "hola"}
           {:id "tag-holas" :name "holas"}
           {:id  "b146d915-3619-4ee6-915f-8317e8629898" :name "Hola"}]))

(defn define-old-and-new-tags [states-of-being-options states-of-being]
  (let [states-of-being* (map #(set/rename-keys % {"key" :id "label" :name})
                              states-of-being)
        old (remove new-tag? states-of-being*)
        {:keys [new possibly-missing]}
        (->> states-of-being*
             (filter new-tag?)
             (map #(assoc % :id (str (random-uuid))))
             (remove-duplicate-tags states-of-being-options))]
    {:old (vec (set (concat old possibly-missing)))
     :new new}))



(comment (define-old-and-new-tags
           [{:id  "b146d915-3619-4ee6-915f-8317e8629898" :name "Hola"}]
           [{:id "tag-hola" :name "hoLa"}
            {:id "tag-holas" :name "holas"}
            #_{:id  "b146d915-3619-4ee6-915f-8317e8629898" :name "Hola"}]))

(defn create-day-quality
  [{:keys [db]} [_ {:keys [year month day states-of-being] :as data}]]
  (js/console.debug "CREATING day quality")
  (let [{:keys [old new]} (define-old-and-new-tags
                            (db :states-of-being)
                            states-of-being)
        updated-db (-> db
                       (update :states-of-being concat new)
                       (assoc-in [:day-qualities year month day]
                                 (assoc data
                                        :states-of-being (concat old new)
                                        :date [year month day]) ))]
    (js/console.log updated-db)
    {:db updated-db
     :fx [[:save-states-of-being]
          [:save-day-qualities]
          [:dispatch [:close-alert]]
          [:day-qualities-modal/close]]}))

(comment (-> (create-day-quality
              {:db @(rf/subscribe [:db])}
              [nil {:year 2020
                    :month 10
                    :day 27
                    :energy-level 4
                    :mood-level 4
                    :productivity-level 4
                    :creativity-level 4
                    :stress-level 4
                    :states-of-being [{:id "tag-hola" :name "hoLa"}
                                      {:id "tag-holas" :name "holas"}]}])
             :db
             (select-keys [:day-qualities :states-of-being])))
