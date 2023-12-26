package tororo1066.dungeontower.skilltree


enum class SkillMenu(val char: Char) {
    CONVENIENCE('\uE028'),
    ATTACK('B')
}

enum class Skill(val char: Char, val type: Type) {
    CONVENIENCE_LARGE_1('\uE029', Type.LARGE),
    CONVENIENCE_LARGE_2('\uE02A', Type.LARGE),
    CONVENIENCE_MIDDLE_1('\uE02B', Type.MIDDLE),
    CONVENIENCE_MIDDLE_2('\uE02C', Type.MIDDLE),
    CONVENIENCE_MIDDLE_3('\uE02D', Type.MIDDLE),
    CONVENIENCE_SMALL_UP('\uE02E', Type.SMALL),
    CONVENIENCE_SMALL_CENTER('\uE02F', Type.SMALL),
    CONVENIENCE_SMALL_DOWN('\uE030', Type.SMALL),
}

enum class Type {
    LARGE,
    MIDDLE,
    SMALL
}