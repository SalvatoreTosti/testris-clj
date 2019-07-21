(ns tetris-clj.core
  (:use
   [tetris-clj.sprites :only [draw-tile draw-tiles load-tiles draw-text]]
   [tetris-clj.utils :only [in? add-vectors inbounds?]]
   [tetris-clj.tetrominoes :only [rotate-tetromino spawn-tetromino]])
  (:require [quil.core :as q]
            [quil.middleware :as m])
  (:gen-class))

(def directions
  {:up [0 -1]
   :down [0 1]
   :left [-1 0]
   :right [1 0]})

(defn make-game []
  {:last-tick (System/currentTimeMillis)
   :tick-length 500 ;;length in miliseconds
   :size [10 16]
   :score 0
   :frozen []
   :tile-map (load-tiles)})

(defn setup []
  (q/background 0)
  (q/frame-rate 30)
  (make-game))

(defn get-active-piece-positions [game]
  (let [position (get-in game [:active-tetromino :position])
        offsets (get-in game [:active-tetromino :offsets])]
    (map #(add-vectors position %) offsets)))

(defn move-tetromino [game dir]
  (let [dir-vec (dir directions)
        new-positions (map #(add-vectors dir-vec %) (get-active-piece-positions game))
        valid (->>  new-positions
                    (map #(inbounds? (get-in game [:size]) %))
                    (every? true?))
        touch-frozen (->> new-positions
                          (map #(in? (get-in game [:frozen]) %))
                          (some true?))]
    (if (and valid (not touch-frozen))
      (update-in game [:active-tetromino :position] #(add-vectors dir-vec %))
      game)))

(defn at-bottom? [game]
  (let [positions (get-active-piece-positions game)
        valid (every? true? (map #(inbounds? (get-in game [:size]) %) positions))
        positions-at-bottom (map #(= (second %) (dec (second (get-in game [:size])))) positions)
        any-at-bottom (some true? positions-at-bottom)
        above-frozen-positions (map #(add-vectors (:up directions) %) (get-in game [:frozen]))
        touch-frozen (some #(in? above-frozen-positions %) positions)]
    (or touch-frozen any-at-bottom)))

(defn freeze-tetromino [game]
  (let [tetromino (get-in game [:active-tetromino])
        position (get-in tetromino [:position])
        positions (map #(add-vectors position %) (:offsets tetromino))]
    (-> game
        (update-in [:frozen] concat positions)
        (assoc-in [:active-tetromino] nil))))

(defn drop-tetromino [game]
  (let [game (move-tetromino game :down)]
    (if (at-bottom? game)
      (freeze-tetromino game)
      game)))

(defn get-row [frozen y]
  (filter #(= y (second %)) frozen))

(defn row-full? [game row-number]
  (-> game
      (get-in [:frozen])
      (get-row row-number)
      count
      (= (first (get-in game [:size])))))

(defn clear-row [game y]
  (let [frozen (get-in game [:frozen])
        upper-half (filter #(> y (second %)) frozen)
        lower-half (filter #(< y (second %)) frozen)
        upper-half (map #(add-vectors (:down directions) %) upper-half)]
    (->> (concat upper-half lower-half)
         (assoc-in game [:frozen]))))

(defn process-row [game width row-number]
  (if (row-full? game row-number)
    (-> game
        (clear-row row-number)
        (process-row width row-number)
        (update :score #(+ % 100)))
    game))

(defn clear-full-rows [game]
  (let [[width height] (get-in game [:size])]
    (loop [game game
           width width
           counter height]
      (if (zero? counter)
        game
        (-> game
            (process-row width counter)
            (recur width (dec counter)))))))

(defn quil-update [game]
  (let [current-time (System/currentTimeMillis)
        next-tick (+ (get-in game [:last-tick])
                     (get-in game [:tick-length]))]
    (if (< current-time next-tick)
      game
      (-> game
          spawn-tetromino
          drop-tetromino
          clear-full-rows
          (assoc-in [:last-tick] current-time)))))

(defn draw-active-piece [game]
  (doseq [position (get-active-piece-positions game)]
    (draw-tile
     position
     (get-in game [:tile-map])
     (get-in game [:active-tetromino :tile-id])
     16
     (get-in game [:active-tetromino :color]))))

(defn draw-frozen-tiles [game]
  (draw-tiles
   (get-in game [:frozen])
   (get-in game [:tile-map])
   :119
   16
   :sky-blue))

(defn clear-screen [game]
  (let [[width height] (get-in game [:size])
        tiles (for [x (range width)
                    y (range height)]
                [x y])]
    (draw-tiles
     tiles
     (get-in game [:tile-map])
     :0
     16)))

(defn pad-string-front [string length padding]
  (let [difference (- length (count string))
        padding (->> (repeat padding)
                     (take difference)
                     (reduce str))]
    (if (neg? difference)
      string
      (str padding string))))


(defn draw-score [game]
  (draw-text 11 0 (get-in game [:tile-map]) "Score")
  (draw-text
    11
    1
    (get-in game [:tile-map])
    (pad-string-front (str (:score game)) 5 "0")))

(defn draw-separator [game]
  (doseq [y (range (second (:size game)))]
    (draw-tile [10 y] (get-in game [:tile-map]) :54 16 :slate)))

(defn draw [game]
  (clear-screen game) ;;make whole screen black
  (draw-active-piece game) ;;:draw the active piece
  (draw-frozen-tiles game) ;;draw all the inactive pieces
  (draw-score game)
  (draw-separator game))

(defn process-input [game key-information]
  (if (nil? (get-in game [:active-tetromino]))
    game
    (->
     (case (:key key-information)
       :a (move-tetromino game :left)
       :d (move-tetromino game :right)
       :s (drop-tetromino game)
       :e (rotate-tetromino game)
       game)
     clear-full-rows)))

(defn -main []
  (q/defsketch game-sketch
               :title "Tetris"
               :size [256 256]
               :setup setup
               :update quil-update
               :draw draw
               :key-pressed process-input
               :middleware [m/fun-mode]))

;; (-main)
