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

    // Modified Vertex Shader to include scale and translate uniforms
    private val vertexShaderCode =
        "attribute vec4 vPosition;" +
        "attribute vec2 vTexCoord;" +
        "uniform float uScale;" +      // New: Uniform for scaling
        "uniform vec2 uTranslate;" +   // New: Uniform for translation
        "varying vec2 TexCoord;" +
        "void main() {" +
        "  gl_Position = vec4(vPosition.xy * uScale + uTranslate, vPosition.zw);" + // Apply scale and translate
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

    // Initial vertices for a full-screen quad.
    private val quadVertices = floatArrayOf(
        -1.0f, -1.0f,  // Bottom Left
         1.0f, -1.0f,  // Bottom Right
        -1.0f,  1.0f,  // Top Left
         1.0f,  1.0f   // Top Right
    )

    // Texture coordinates
    private val texCoords = floatArrayOf(
        0.0f, 1.0f, // Bottom Left
        1.0f, 1.0f, // Bottom Right
        0.0f, 0.0f, // Top Left
        1.0f, 0.0f  // Top Right
    )

    @Volatile var currentScale = 1.0f
    @Volatile var currentTranslateX = 0.0f
    @Volatile var currentTranslateY = 0.0f

    private var imageUri: Uri? = null
    private var surfaceView: GLSurfaceView? = null

    // Viewport and image dimensions
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    fun setImageUri(uri: Uri, glSurfaceView: GLSurfaceView) {
        this.imageUri = uri
        this.surfaceView = glSurfaceView
        // Reset transformations for new image
        currentScale = 1.0f
        currentTranslateX = 0.0f
        currentTranslateY = 0.0f
        // Request a render to load the new texture
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
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f) // Black background

        // Initialize vertex byte buffer for shape coordinates
        val bb = ByteBuffer.allocateDirect(quadVertices.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(quadVertices)
        vertexBuffer.position(0)

        // Initialize texture coordinate byte buffer
        val tcb = ByteBuffer.allocateDirect(texCoords.size * 4)
        tcb.order(ByteOrder.nativeOrder())
        texCoordBuffer = tcb.asFloatBuffer()
        texCoordBuffer.put(texCoords)
        texCoordBuffer.position(0)

        // Create and link shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        // Create texture object
        GLES20.glGenTextures(1, textureIds, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Load bitmap and bind texture if a new imageUri is available
        imageUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    imageWidth = bitmap.width
                    imageHeight = bitmap.height
                    loadTexture(bitmap)
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            imageUri = null // Clear URI after loading to prevent reloading every frame
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        
        // Calculate base vertices to maintain aspect ratio (without gesture transforms yet)
        val adjustedVertices = calculateAdjustedVertices()
        vertexBuffer.put(adjustedVertices).position(0)

        // Get handle to vertex shader's vPosition member
        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)

        // Get handle to texture coordinates
        val texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)
        
        // Get handles to uniforms
        val scaleHandle = GLES20.glGetUniformLocation(program, "uScale")
        val translateHandle = GLES20.glGetUniformLocation(program, "uTranslate")
        val textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        
        // Pass uniform values
        GLES20.glUniform1f(scaleHandle, currentScale)
        GLES20.glUniform2f(translateHandle, currentTranslateX, currentTranslateY)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glUniform1i(textureHandle, 0)

        // Draw the quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
    }

    private fun calculateAdjustedVertices(): FloatArray {
        if (viewportWidth == 0 || viewportHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            return quadVertices // Return default if dimensions are not ready
        }

        val viewportRatio = viewportWidth.toFloat() / viewportHeight.toFloat()
        val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()

        var scaleX = 1.0f
        var scaleY = 1.0f

        if (imageRatio > viewportRatio) {
            // Image is wider than the viewport, scale height down to fit
            scaleY = viewportRatio / imageRatio
        } else {
            // Image is taller than or equal to the viewport, scale width down to fit
            scaleX = imageRatio / viewportRatio
        }

        // These base vertices maintain aspect ratio, actual gesture transforms are in shader
        return floatArrayOf(
            -scaleX, -scaleY,  // Bottom Left
             scaleX, -scaleY,  // Bottom Right
            -scaleX,  scaleY,  // Top Left
             scaleX,  scaleY   // Top Right
        )
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    private fun loadTexture(bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])

        // Set filtering
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // Load the bitmap into the bound texture.
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }
}
