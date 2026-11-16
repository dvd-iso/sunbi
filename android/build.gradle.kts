plugins {
    id("com.android.application") version "9.5.1" apply false
    // O AGP 9 já compila Kotlin sozinho; declarar o KGP aqui só fixa a versão do Kotlin.
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
