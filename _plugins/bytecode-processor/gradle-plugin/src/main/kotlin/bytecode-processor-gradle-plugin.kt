@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.gradleplugin

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.util.TokenBuffer
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.reformator.bytecodeprocessor.api.BytecodeProcessorContext
import dev.reformator.bytecodeprocessor.api.applyBytecodeProcessors
import dev.reformator.bytecodeprocessor.api.Processor
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.File
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import kotlin.reflect.jvm.javaGetter

internal const val EXTENSION_NAME = "bytecodeProcessor"
internal const val INIT_TASK_NAME = "bytecodeProcessorInit"
internal const val MERGE_CONTEXTS_TASK_NAME = "bytecodeProcessorMergeContexts"


open class BytecodeProcessorPluginExtension {
    var dependentProjects: Collection<Project> = emptyList()
    var processors: Collection<Processor> = emptyList()
    var skipUpdate = false

    @Suppress("unused")
    fun initContext(action: BytecodeProcessorContext.() -> Unit) {
        initContextTasks.add(action)
    }

    internal val initContextTasks = mutableListOf<BytecodeProcessorContext.() -> Unit>()
}

@CacheableTask
abstract class BytecodeProcessorMergeContextsTask @Inject constructor(
    private val contextModifier: (MapperBytecodeProcessorContext.() -> Unit)
): DefaultTask() {
    @get:OutputFile
    abstract val mergedContextsFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contextFilesToMerge: ConfigurableFileCollection

    @TaskAction
    fun action() {
        val context = MapperBytecodeProcessorContext()
        contextFilesToMerge.forEach { file ->
            if (file.exists()) {
                val fileContext = MapperBytecodeProcessorContext.read(file)
                context.merge(fileContext)
            }
        }
        context.contextModifier()
        context.write(mergedContextsFile.get().asFile)
    }
}

@CacheableTask
abstract class BytecodeProcessorMergeContextsNoModifierTask(): BytecodeProcessorMergeContextsTask({ })

@Suppress("unused")
class BytecodeProcessorPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val extension = extensions.create(EXTENSION_NAME, BytecodeProcessorPluginExtension::class.java)

        val loadDependentProjectsTask = tasks.register(
            INIT_TASK_NAME,
            BytecodeProcessorMergeContextsTask::class.java,
            *arrayOf({ context: MapperBytecodeProcessorContext ->
                extension.initContextTasks.forEach { context.it() }
            })
        )
        loadDependentProjectsTask.configure { task ->
            task.mergedContextsFile.set(bytecodeProcessorDirectory.get().file("initialContext.json"))
        }

        val mergeContextsTask = tasks.register(
            MERGE_CONTEXTS_TASK_NAME,
            BytecodeProcessorMergeContextsNoModifierTask::class.java
        ) { task ->
            task.mergedContextsFile.set(bytecodeProcessorDirectory.get().file("mergedContext.json"))
        }

        afterEvaluate { _ ->
            val dependentProjectsMergedContextsFiles = mutableListOf<RegularFileProperty>()
            extension.dependentProjects.forEach { dependentProject ->
                val dependentProjectAction = Action<Project> { _ ->
                    val dependentProjectMergeContextsTask = dependentProject.mergedContextsTask
                    loadDependentProjectsTask.configure { task ->
                        task.dependsOn(dependentProjectMergeContextsTask)
                    }
                    dependentProjectsMergedContextsFiles.add(dependentProjectMergeContextsTask.mergedContextsFile)
                }
                if (dependentProject.state.executed) {
                    dependentProjectAction.execute(dependentProject)
                } else {
                    dependentProject.afterEvaluate(dependentProjectAction)
                }
            }

            loadDependentProjectsTask.configure { task ->
                task.contextFilesToMerge.setFrom(dependentProjectsMergedContextsFiles)
            }

            val contextFiles = mutableListOf<RegularFile>()
            fun updateCompileTask(task: Task, dir: DirectoryProperty) {
                mergeContextsTask.configure { it.dependsOn(task) }
                task.dependsOn(loadDependentProjectsTask)
                val contextFile = bytecodeProcessorDirectory.get().file("compileContext_${task.name}.json")
                val contextFileAsJavaFile = contextFile.asFile
                if (contextFileAsJavaFile.isDirectory) {
                    contextFileAsJavaFile.deleteRecursively()
                }
                task.outputs.file(contextFile)
                contextFiles.add(contextFile)
                task.doLast { _ ->
                    val context = MapperBytecodeProcessorContext.read(
                        from = loadDependentProjectsTask.get().mergedContextsFile.get().asFile
                    )
                    dir.get().asFile.applyBytecodeProcessors(
                        processors = extension.processors,
                        context = context,
                        skipUpdate = extension.skipUpdate
                    )
                    contextFileAsJavaFile.delete()
                    context.write(contextFileAsJavaFile)
                }
            }

            tasks.withType(AbstractCompile::class.java) { task ->
                updateCompileTask(task, task.destinationDirectory)
            }
            tasks.withType(KotlinJvmCompile::class.java) { task ->
                updateCompileTask(task, task.destinationDirectory)
            }
            mergeContextsTask.configure { task ->
                task.contextFilesToMerge.setFrom(contextFiles)
            }
        }
    }
}

private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(MapAsArrayModule).apply {
    enable(SerializationFeature.INDENT_OUTPUT)
}

private object MapAsArrayModule: SimpleModule() {
    private const val KEY = "key"
    private const val VALUE = "value"

    init {
        addSerializer(Map::class.java, Serializer)
        addDeserializer(Map::class.java, Deserializer.default)
    }

    object Serializer: StdSerializer<Map<*, *>>(Map::class.java) {
        override fun serialize(value: Map<*, *>, gen: JsonGenerator, provider: SerializerProvider) {
            if (value.keys.all { isSerializeToString(it, provider) }) {
                gen.writeStartObject()
                value.forEach { (key, value) ->
                    key!!
                    provider.findKeySerializer(key.javaClass, null).serialize(
                        key,
                        gen,
                        provider
                    )
                    provider.defaultSerializeValue(value, gen)
                }
                gen.writeEndObject()
            } else {
                gen.writeStartArray()
                value.forEach { (key, value) ->
                    gen.writeStartObject()
                    gen.writeFieldName(KEY)
                    provider.defaultSerializeValue(key, gen)
                    gen.writeFieldName(VALUE)
                    provider.defaultSerializeValue(value, gen)
                    gen.writeEndObject()
                }
                gen.writeEndArray()
            }
        }

        private fun isSerializeToString(key: Any?, provider: SerializerProvider): Boolean {
            val tokens = TokenBuffer(objectMapper, false)
            provider.defaultSerializeValue(key, tokens)
            return tokens.firstToken() == JsonToken.VALUE_STRING
        }

        @Suppress("unused")
        private fun readResolve(): Any = Serializer
    }

    class Deserializer(
        private val keyType: JavaType? = null,
        private val valueType: JavaType? = null
    ): StdDeserializer<Map<*, *>>(Map::class.java), ContextualDeserializer {
        companion object {
            val default = Deserializer()
        }

        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Map<*, *> {
            val result = mutableMapOf<Any?, Any?>()
            when (p.currentToken) {
                JsonToken.START_OBJECT -> {
                    while (p.nextToken() != JsonToken.END_OBJECT) {
                        val key = run {
                            val key = p.currentName()
                            if (keyType != null) {
                                val keyParser = ctxt.parser.codec.treeAsTokens(TextNode(key))
                                keyParser.nextToken()
                                ctxt.readValue(keyParser, keyType)
                            } else key
                        }
                        p.nextToken()
                        val value: Any? = if (valueType != null) {
                            ctxt.readValue<Any?>(p, valueType)
                        } else {
                            ctxt.readValue(p, JsonNode::class.java)
                        }
                        result[key] = value
                    }
                }
                JsonToken.START_ARRAY -> {
                    while (p.nextToken() != JsonToken.END_ARRAY) {
                        if (p.currentToken() != JsonToken.START_OBJECT) {
                            throw ctxt.wrongTokenException (p, null as Class<*>?, JsonToken.START_OBJECT, null)
                        }
                        var key: Any? = null
                        var value: Any? = null
                        var keySet = false
                        var valueSet = false
                        while (p.nextToken() != JsonToken.END_OBJECT) {
                            when (p.currentName()) {
                                KEY -> {
                                    if (keySet) throw ctxt.weirdKeyException(null, KEY, "Duplicate key: [$KEY]")
                                    p.nextToken()
                                    key = if (keyType != null) {
                                        ctxt.readValue<Any?>(p, keyType)
                                    } else {
                                        ctxt.readValue(p, JsonNode::class.java)
                                    }
                                    keySet = true
                                }
                                VALUE -> {
                                    if (valueSet) throw ctxt.weirdKeyException(null, VALUE, "Duplicate key: $VALUE")
                                    p.nextToken()
                                    value = if (valueType != null) {
                                        ctxt.readValue<Any?>(p, valueType)
                                    } else {
                                        ctxt.readValue(p, JsonNode::class.java)
                                    }
                                    valueSet = true
                                }
                                else -> throw ctxt.weirdKeyException(null, p.currentName(), "Unknown key: ${p.currentName()}")
                            }
                        }
                        if (!keySet) throw ctxt.weirdKeyException(null, KEY, "Key is not set")
                        if (!valueSet) throw ctxt.weirdKeyException(null, VALUE, "Value is not set for key [$key]")
                        result[key] = value
                    }
                }
                else -> throw ctxt.wrongTokenException(p, null as Class<*>?, JsonToken.START_OBJECT, null)
            }
            return result
        }

        override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): Deserializer {
            val mapType: JavaType? = property?.type ?: ctxt.contextualType
            if (mapType != null && mapType.isMapLikeType) {
                val keyType: JavaType? = mapType.keyType
                val valueType: JavaType? = mapType.contentType
                if (keyType != null || valueType != null) {
                    return Deserializer(keyType, valueType)
                }
            }
            return default
        }
    }


    @Suppress("unused")
    private fun readResolve(): Any = MapAsArrayModule
}


@JvmInline
value class MapperBytecodeProcessorContext private constructor(
    private val root: MutableMap<String, List<JsonNode>>
): BytecodeProcessorContext {
    constructor(): this(hashMapOf())

    companion object {
        private val rootTypeRef = object: TypeReference<MutableMap<String, List<JsonNode>>>() { }

        fun read(from: File): MapperBytecodeProcessorContext {
            val root = objectMapper.readValue(from, rootTypeRef)
            return MapperBytecodeProcessorContext(root)
        }
    }

    override fun <T: Any> get(key: BytecodeProcessorContext.Key<T>): T {
        var keyNode = root[key.id] ?: return key.default
        if (keyNode.isEmpty()) return key.default
        val keyType = key.jacksonType
        if (keyNode.size > 1) {
            val merged = keyNode.asSequence()
                .map { objectMapper.convertValue<T>(it, keyType) }
                .reduce(key::merge)
            if (key.isEmpty(merged)) {
                root.remove(key.id)
                return merged
            }
            keyNode = listOf(objectMapper.convertValue(merged, JsonNode::class.java))
            root[key.id] = keyNode
        }
        return objectMapper.convertValue(keyNode[0], key.jacksonType)
    }

    override fun <T: Any> merge(key: BytecodeProcessorContext.Key<T>, value: T): T {
        val newValue = key.merge(this[key], value)
        if (key.isEmpty(newValue)) {
            root.remove(key.id)
        } else {
            root[key.id] = listOf(objectMapper.convertValue(newValue, JsonNode::class.java))
        }
        return newValue
    }

    fun write(to: File) {
        objectMapper.writeValue(to, root)
    }

    fun merge(context: MapperBytecodeProcessorContext) {
        context.root.forEach { (keyId, otherValues) ->
            val values = root[keyId]
            root[keyId] = if (values.isNullOrEmpty()) {
                otherValues
            } else {
                values + otherValues
            }
        }
    }
}

private val BytecodeProcessorContext.Key<*>.jacksonType: JavaType
    get() {
        javaClass.genericInterfaces.forEach { interfaceType ->
            if (interfaceType !is ParameterizedType) return@forEach
            if (interfaceType.rawType != BytecodeProcessorContext.Key::class.java) return@forEach
            return objectMapper.typeFactory.constructType(interfaceType.actualTypeArguments[0])
        }
        error("Key type is not found for class [${javaClass}] instance [$this]")
    }

private val mergedContextsFileJavaGetterName = BytecodeProcessorMergeContextsTask::mergedContextsFile.javaGetter!!.name

private val Project.mergedContextsTask: Task
    get() = tasks.getByName(MERGE_CONTEXTS_TASK_NAME)

private val Task.mergedContextsFile: RegularFileProperty
    get() {
        val mergedContextsFileGetterMethod = javaClass.getDeclaredMethod(mergedContextsFileJavaGetterName)
        return mergedContextsFileGetterMethod.invoke(this) as RegularFileProperty
    }

private val Project.bytecodeProcessorDirectory: Provider<Directory>
    get() = layout.buildDirectory.dir("bytecodeProcessor")
