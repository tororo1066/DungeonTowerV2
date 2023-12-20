package tororo1066.dungeontower.skilltree


enum class SkillMenu(val char: Char) {
    CONVENIENCE('\uE028'),
    ATTACK('B')
}

enum class LargeSkill(val char: Char) {
    CONVENIENCE_1('\uE029'),
    CONVENIENCE_2('\uE02A'),
}

enum class MiddleSkill(val char: Char) {
    CONVENIENCE_1('\uE02B'),
    CONVENIENCE_2('\uE02C'),
    CONVENIENCE_3('\uE02D'),
}

enum class SmallSkill(val char: Char) {
    CONVENIENCE_UP('\uE02E'),
    CONVENIENCE_CENTER('\uE02F'),
    CONVENIENCE_DOWN('\uE030'),
}