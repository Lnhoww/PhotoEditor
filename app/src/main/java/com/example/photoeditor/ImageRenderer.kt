package com.example.photoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min

class ImageRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private val vertexShaderCode =
        "attribute vec4 vPosition;" +
        "attribute vec2 vTexCoord;" +
        "uniform float uScale;" +
        "uniform vec2 uTranslate;" +
        "varying vec2 TexCoord;" +
        "void main() {" +
        "  gl_Position = vec4(vPosition.xy * uScale + uTranslate, vPosition.zw);" +
        "  TexCoord = vTexCoord;" +
        "}"

    private val fragmentShaderCode =
        "precision mediump float;" +
        "uniform sampler2D uTexture;" +
        "varying vec2 TexCoord;" +
        "void main() {" +
        "  gl_FragColor = texture2D(uTexture, TexCoord);" +
        "}"

    private var program: Int = 0
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer
    private val textureIds = IntArray(1)

    private val quadVertices = floatArrayOf( -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f )
    private val texCoords = floatArrayOf( 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f )

    @Volatile var currentScale = 1.0f
    @Volatile var currentTranslateX = 0.0f
    @Volatile var currentTranslateY = 0.0f

    private var _imageUri: Uri? = null
    private var currentLoadedUri: Uri? = null
    private var surfaceView: GLSurfaceView? = null

    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    fun setImageUri(uri: Uri, glSurfaceView: GLSurfaceView) {
        _imageUri = uri
        surfaceView = glSurfaceView
        if (uri != currentLoadedUri) {
            currentScale = 1.0f
            currentTranslateX = 0.0f
            currentTranslateY = 0.0f
        }
        glSurfaceView.requestRender()
    }

    fun setScale(scale: Float) {
        currentScale = scale
        surfaceView?.requestRender()
    }

    fun setTranslation(x: Float, y: Float) {
        currentTranslateX = x
        currentTranslateY = y
        surfaceView?.requestRender()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        val bb = ByteBuffer.allocateDirect(quadVertices.size * 4).order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer().apply { put(quadVertices); position(0) }

        val tcb = ByteBuffer.allocateDirect(texCoords.size * 4).order(ByteOrder.nativeOrder())
        texCoordBuffer = tcb.asFloatBuffer().apply { put(texCoords); position(0) }

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        GLES20.glGenTextures(1, textureIds, 0)

        // IMPORTANT: When surface is re-created, the texture data on GPU is lost.
        // We must force a reload on the next onDrawFrame.
        currentLoadedUri = null
    }

    override fun onDrawFrame(gl: GL10?) {
        _imageUri?.let { uri ->
            if (uri != currentLoadedUri) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        imageWidth = bitmap.width
                        imageHeight = bitmap.height
                        loadTexture(bitmap)
                        bitmap.recycle()
                        currentLoadedUri = uri
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    currentLoadedUri = null
                }
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        
        val adjustedVertices = calculateAdjustedVertices()
        vertexBuffer.put(adjustedVertices).position(0)

        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)

        val texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)
        
        val scaleHandle = GLES20.glGetUniformLocation(program, "uScale")
        val translateHandle = GLES20.glGetUniformLocation(program, "uTranslate")
        val textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        
        GLES20.glUniform1f(scaleHandle, currentScale)
        GLES20.glUniform2f(translateHandle, currentTranslateX, currentTranslateY)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        // This is a reliable place to request a render, as the surface is guaranteed to exist.
        surfaceView?.requestRender()
    }

    private fun calculateAdjustedVertices(): FloatArray {
        if (viewportWidth == 0 || viewportHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            return quadVertices
        }

        val viewportRatio = viewportWidth.toFloat() / viewportHeight.toFloat()
        val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()

        var scaleX = 1.0f
        var scaleY = 1.0f

        if (imageRatio > viewportRatio) {
            scaleY = viewportRatio / imageRatio
        } else {
            scaleX = imageRatio / viewportRatio
        }

        return floatArrayOf(-scaleX, -scaleY, scaleX, -scaleY, -scaleX, scaleY, scaleX, scaleY)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    private fun loadTexture(bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }
}
