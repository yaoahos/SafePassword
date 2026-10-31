# SafePassword R8 / ProGuard 规则
#
# 本项目 release 构建启用 R8（isMinifyEnabled / isShrinkResources = true）。
# 依赖方（Compose、OkHttp、Ktor、androidx）均自带 consumer 规则，
# 这里只补 kotlinx.serialization 生成的序列化器所需规则。

# 保留注解、内部类与泛型签名信息（序列化描述符需要）
-keepattributes *Annotation*, InnerClasses, Signature
# 保留崩溃堆栈的可读性
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-dontnote kotlinx.serialization.**

# androidx.security（EncryptedSharedPreferences）经 Tink 引用的编译期注解，运行期不存在
-dontwarn com.google.errorprone.annotations.**

# 应用内所有 @Serializable 类：保留 Companion 与生成的 serializer 方法
-keepclassmembers @kotlinx.serialization.Serializable class com.safeincloud.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# 生成的 $$serializer 类需完整保留（含其 descriptor 字段）
-keep,includedescriptorclasses class com.safeincloud.**$$serializer { *; }

# 序列化中用到的枚举：保留全部成员，避免 values()/valueOf() 被裁剪
-keepclassmembers enum com.safeincloud.** {
    *;
}
