package dev.bpmcrafters.processengine.worker.registrar


enum class MyEnum {
    ONE, TWO, THREE;

    class Converter: VariableConverter {
        override fun <T : Any> mapToType(value: Any?, type: Class<T>): T {
            requireNotNull(value)
            return MyEnum.valueOf(value as String) as T
        }

    }

}