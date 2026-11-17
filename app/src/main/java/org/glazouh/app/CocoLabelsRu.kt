package org.glazouh.app

/** Russian voice/UI labels for the 80 COCO categories used by EfficientDet-Lite0. */
internal object CocoLabelsRu {
    private val labels = mapOf(
        "person" to "человек", "bicycle" to "велосипед", "car" to "автомобиль",
        "motorcycle" to "мотоцикл", "airplane" to "самолёт", "bus" to "автобус",
        "train" to "поезд", "truck" to "грузовик", "boat" to "лодка",
        "traffic light" to "светофор", "fire hydrant" to "пожарный гидрант",
        "stop sign" to "знак стоп", "parking meter" to "парковочный автомат",
        "bench" to "скамейка", "bird" to "птица", "cat" to "кошка",
        "dog" to "собака", "horse" to "лошадь", "sheep" to "овца",
        "cow" to "корова", "elephant" to "слон", "bear" to "медведь",
        "zebra" to "зебра", "giraffe" to "жираф", "backpack" to "рюкзак",
        "umbrella" to "зонт", "handbag" to "сумка", "tie" to "галстук",
        "suitcase" to "чемодан", "frisbee" to "фрисби", "skis" to "лыжи",
        "snowboard" to "сноуборд", "sports ball" to "спортивный мяч",
        "kite" to "воздушный змей", "baseball bat" to "бейсбольная бита",
        "baseball glove" to "бейсбольная перчатка", "skateboard" to "скейтборд",
        "surfboard" to "доска для сёрфинга", "tennis racket" to "теннисная ракетка",
        "bottle" to "бутылка", "wine glass" to "бокал", "cup" to "чашка",
        "fork" to "вилка", "knife" to "нож", "spoon" to "ложка",
        "bowl" to "миска", "banana" to "банан", "apple" to "яблоко",
        "sandwich" to "бутерброд", "orange" to "апельсин", "broccoli" to "брокколи",
        "carrot" to "морковь", "hot dog" to "хот-дог", "pizza" to "пицца",
        "donut" to "пончик", "cake" to "торт", "chair" to "стул",
        "couch" to "диван", "potted plant" to "комнатное растение",
        "bed" to "кровать", "dining table" to "обеденный стол",
        "toilet" to "унитаз", "tv" to "телевизор", "laptop" to "ноутбук",
        "mouse" to "компьютерная мышь", "remote" to "пульт",
        "keyboard" to "клавиатура", "cell phone" to "мобильный телефон",
        "microwave" to "микроволновка", "oven" to "духовка",
        "toaster" to "тостер", "sink" to "раковина",
        "refrigerator" to "холодильник", "book" to "книга",
        "clock" to "часы", "vase" to "ваза", "scissors" to "ножницы",
        "teddy bear" to "плюшевый мишка", "hair drier" to "фен",
        "toothbrush" to "зубная щётка"
    )

    fun displayName(label: String): String = labels[label] ?: label
}
