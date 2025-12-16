package com.example.photoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ImageRenderer(private val context: Context) : GLSurfaceView.Renderer {

    // 1. 顶点着色器 (Vertex Shader)
    private val vertexShaderCode =
        "attribute vec4 vPosition;" +       // 输入：顶点坐标 (矩形的四个角)
        "attribute vec2 vTexCoord;" +       // 输入：纹理坐标 (对应图片的哪个点)
        "uniform float uScale;" +           // 输入：用户缩放比例 (由手势控制)
        "uniform vec2 uTranslate;" +        // 输入：用户平移距离 (由手势控制)
        "varying vec2 TexCoord;" +          // 输出：传给片段着色器的变量
        "void main() {" +
        "  gl_Position = vec4(vPosition.xy * uScale + uTranslate, vPosition.zw);" +
        "  TexCoord = vTexCoord;" +
        "}"

    private val fragmentShaderCode = """
    precision mediump float;
    uniform sampler2D uTexture;
    uniform int uFilterType; // [新增] 接收滤镜类型参数：0=原图, 1=黑白, 2=暖色, 3=冷色
    varying vec2 TexCoord;
    
    void main() {
        vec4 color = texture2D(uTexture, TexCoord);
        
        if (uFilterType == 1) { 
            // === 黑白滤镜 (Luma转换算法) ===
            // 人眼对绿光最敏感，所以绿色权重最高 (0.587)
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            gl_FragColor = vec4(vec3(gray), color.a);
            
        } else if (uFilterType == 2) { 
            // === 暖色/怀旧滤镜 ===
            // 简单粗暴地增加红色和绿色通道
            gl_FragColor = vec4(color.r + 0.1, color.g + 0.05, color.b, color.a);
            
        } else if (uFilterType == 3) {
            // === 冷色/胶片滤镜 ===
            // 增加蓝色通道
            gl_FragColor = vec4(color.r, color.g, color.b + 0.1, color.a);
            
        } else {
            // === 原图 (Type 0) ===
            gl_FragColor = color;
        }
    }
""".trimIndent()

    private var program: Int = 0
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer
    private val textureIds = IntArray(1)

    private val quadVertices = floatArrayOf(
        -1.0f, -1.0f, // 左下
        1.0f, -1.0f,  // 右下
        -1.0f, 1.0f,  // 左上
        1.0f, 1.0f    // 右上
    )

    // 默认纹理坐标 (V=0 是顶部)
    private val defaultTexCoords = floatArrayOf(
        0.0f, 1.0f, // 左下
        1.0f, 1.0f, // 右下
        0.0f, 0.0f, // 左上
        1.0f, 0.0f  // 右上
    )

    // --- 变换参数 ---
    @Volatile private var baseScaleX = 1.0f
    @Volatile private var baseScaleY = 1.0f
    @Volatile private var userScale = 1.0f
    @Volatile private var userTranslateX = 0.0f
    @Volatile private var userTranslateY = 0.0f
    // 2. 新增一个变量记录当前滤镜
    @Volatile private var currentFilterType = 0

    // --- 状态管理 ---
    private var _imageUri: Uri? = null
    private var currentLoadedUri: Uri? = null
    var surfaceView: GLSurfaceView? = null

    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    // 存储归一化的裁剪区域 (0.0 到 1.0)
    // Left/Top 是 0, Right/Bottom 是 1 代表全图
    @Volatile private var normalizedCropRect: Rect? = null

    fun setImageUri(uri: Uri, glSurfaceView: GLSurfaceView) {
        _imageUri = uri
        surfaceView = glSurfaceView
        if (uri != currentLoadedUri) {
            userScale = 1.0f
            userTranslateX = 0.0f
            userTranslateY = 0.0f
            normalizedCropRect = null
            currentLoadedUri = null
        }
        glSurfaceView.requestRender()
    }

    fun setScale(scale: Float) {
        userScale = scale
        surfaceView?.requestRender()
    }

    fun setTranslation(x: Float, y: Float) {
        userTranslateX = x
        userTranslateY = y
        surfaceView?.requestRender()
    }

    // 新方法：接收归一化坐标 (0.0 - 1.0)
    fun setNormalizedCropRect(rect: Rect?) {
        normalizedCropRect = rect
        // 关键：更新完裁剪区域后，必须重新计算 baseScale，否则形状会拉伸
        updateBaseScale()
        surfaceView?.requestRender()
    }

    // 兼容旧接口，虽然 EditorScreen 可能不再用它
    fun setTransform(scale: Float, offset: Offset) {
        userScale = scale
        userTranslateX = offset.x
        userTranslateY = offset.y
        surfaceView?.requestRender()
    }

    // 只要参数变了，就请求重绘
    fun setFilter(filterType: Int) {
        if (currentFilterType != filterType) {
            currentFilterType = filterType
            surfaceView?.requestRender()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        val bb = ByteBuffer.allocateDirect(quadVertices.size * 4).order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer().apply { put(quadVertices); position(0) }

        val tcb = ByteBuffer.allocateDirect(defaultTexCoords.size * 4).order(ByteOrder.nativeOrder())
        texCoordBuffer = tcb.asFloatBuffer().apply { put(defaultTexCoords); position(0) }

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        GLES20.glGenTextures(1, textureIds, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        updateBaseScale()
        surfaceView?.requestRender()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        _imageUri?.let { uri ->
            if (uri != currentLoadedUri) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            imageWidth = bitmap.width
                            imageHeight = bitmap.height
                            loadTexture(bitmap)
                            bitmap.recycle()
                            currentLoadedUri = uri
                            updateBaseScale() // 图片加载完也要重新计算比例
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (imageWidth == 0 || imageHeight == 0) return

        GLES20.glUseProgram(program)

        // 1. 处理纹理坐标 (决定显示图片的哪一部分)
        val currentTexCoords = if (normalizedCropRect != null) {
            val rect = normalizedCropRect!!
            // Android Rect 坐标系: Left/Top 是起点
            // OpenGL 纹理坐标: U (0->1 左到右), V (0->1 上到下, 或者是下到上取决于 loadTexture)
            // 在我们的 defaultTexCoords 中，V=0 是 Top，V=1 是 Bottom。
            // 所以直接映射即可：
            floatArrayOf(
                rect.left, rect.bottom, // 左下
                rect.right, rect.bottom, // 右下
                rect.left, rect.top,    // 左上
                rect.right, rect.top     // 右上
            )
        } else {
            defaultTexCoords
        }
        texCoordBuffer.put(currentTexCoords).position(0)

        // 2. 更新顶点几何形状 (决定显示的矩形框长宽比)
        // 这一步是修复拉伸问题的关键
        val vertices = floatArrayOf(
            -baseScaleX, -baseScaleY, // 左下
            baseScaleX, -baseScaleY, // 右下
            -baseScaleX,  baseScaleY, // 左上
            baseScaleX,  baseScaleY  // 右上
        )
        vertexBuffer.put(vertices).position(0)

        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)

        val texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)

        val scaleHandle = GLES20.glGetUniformLocation(program, "uScale")
        val translateHandle = GLES20.glGetUniformLocation(program, "uTranslate")
        val textureHandle = GLES20.glGetUniformLocation(program, "uTexture")

        GLES20.glUniform1f(scaleHandle, userScale)
        GLES20.glUniform2f(translateHandle, userTranslateX, userTranslateY)

        // [新增] 获取 uFilterType 的位置，并赋值
        val filterTypeHandle = GLES20.glGetUniformLocation(program, "uFilterType")
        GLES20.glUniform1i(filterTypeHandle, currentFilterType)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    // 核心修复逻辑：根据是否裁剪，计算正确的宽高比
    private fun updateBaseScale() {
        if (viewportWidth == 0 || viewportHeight == 0 || imageWidth == 0 || imageHeight == 0) return

        // 1. 确定当前要显示内容的宽高
        val contentWidth: Float
        val contentHeight: Float

        if (normalizedCropRect != null) {
            // 如果有裁剪，宽高是原图宽高 * 裁剪比例
            contentWidth = imageWidth * normalizedCropRect!!.width
            contentHeight = imageHeight * normalizedCropRect!!.height
        } else {
            // 没有裁剪，就是原图宽高
            contentWidth = imageWidth.toFloat()
            contentHeight = imageHeight.toFloat()
        }

        // 2. 计算 Fit Center (居中适应) 比例
        val viewportRatio = viewportWidth.toFloat() / viewportHeight.toFloat()
        val contentRatio = contentWidth / contentHeight

        if (contentRatio > viewportRatio) {
            // 内容比屏幕宽：宽度撑满 (1.0)，高度按比例缩小
            baseScaleX = 1.0f
            baseScaleY = viewportRatio / contentRatio
        } else {
            // 内容比屏幕高：高度撑满 (1.0)，宽度按比例缩小
            baseScaleX = contentRatio / viewportRatio
            baseScaleY = 1.0f
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    private fun loadTexture(bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }
}