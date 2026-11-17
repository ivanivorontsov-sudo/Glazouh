package org.glazouh.app

object SoundEventLabelsRu {
    private val labels = mapOf(
        "Alarm" to "Сигнал тревоги",
        "Alarm clock" to "Будильник",
        "Siren" to "Сирена",
        "Fire alarm" to "Пожарная сигнализация",
        "Smoke detector, smoke alarm" to "Датчик дыма",
        "Doorbell" to "Звонок в дверь",
        "Knock" to "Стук в дверь",
        "Dog" to "Собака",
        "Bark" to "Лай собаки",
        "Car horn, honking" to "Автомобильный сигнал",
        "Glass" to "Звук стекла",
        "Breaking" to "Звук разбивания",
        "Baby cry, infant cry" to "Плач ребёнка",
        "Telephone bell ringing" to "Телефонный звонок",
        "Rain" to "Дождь",
        "Thunder" to "Гром",
        "Footsteps" to "Шаги",
        "Applause" to "Аплодисменты",
        "Clapping" to "Хлопки",
        "Crying, sobbing" to "Плач",
        "Screaming" to "Крик",
        "Shout" to "Громкий крик",
        "Yell" to "Выкрик",
        "Speech" to "Речь",
        "Music" to "Музыка",
        "Vehicle" to "Транспорт",
        "Engine" to "Работа двигателя",
        "Emergency vehicle" to "Спецтранспорт",
        "Fire engine, fire truck (siren)" to "Пожарная машина с сиреной",
        "Police car (siren)" to "Полицейская машина с сиреной",
        "Ambulance (siren)" to "Скорая помощь с сиреной",
        "Train" to "Поезд",
        "Train horn" to "Гудок поезда",
        "Bird" to "Птица",
        "Cat" to "Кошка",
        "Cough" to "Кашель",
        "Sneezing" to "Чихание",
        "Laughter" to "Смех",
        "Toilet flush" to "Смыв воды",
        "Water" to "Звук воды"
    )

    fun translate(label: String): String = labels[label] ?: label
}
