package app.cloudlight.buildlogic;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Texture delivery adjustments for WebRTC 144.7559.x. The decoder calls both metadata
 * setters on every output. Upstream resets SurfaceTexture's buffer size and posts two
 * handler messages even when neither value changed. Cache requested (not asynchronously
 * applied) values, preserving the first update, transitions and upstream validation.
 *
 * The decoder also hands out one texture at a time and throws a decoded picture away when the
 * previous one has not been drawn yet, which at 120 fps happens to about one frame in four.
 * Three calls into the app's VideoHooks fix that: the decoder waits a moment for the previous
 * picture instead of dropping the new one, reports when a picture is handed on, and builds
 * its MediaFormat through the app so the frame rate can be declared.
 */
public final class WebRtcTextureDelivery extends ClassVisitor implements Opcodes {
    public static final String TEXTURE_HELPER = "org/webrtc/SurfaceTextureHelper";
    public static final String EGL_THREAD = "org/webrtc/EglThread";
    public static final String DECODER = "org/webrtc/AndroidVideoDecoder";
    /** The app class the patched decoder calls into; keep its three methods in step. */
    public static final String HOOKS = "app/cloudlight/tv/stream/VideoHooks";
    private static final String DELIVER_DESC = "(ILandroid/media/MediaCodec$BufferInfo;ILjava/lang/Integer;)V";
    private static final String FORMAT_DESC = "(Ljava/lang/String;II)Landroid/media/MediaFormat;";
    private final String owner;
    private int sizeMethods;
    private int rotationMethods;
    private int threadConstructors;
    private int helperInitializations;
    private int textureGates;
    private int textureHandovers;
    private int formatCalls;

    public WebRtcTextureDelivery(ClassVisitor next, String owner) {
        super(ASM9, next);
        this.owner = owner;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
            String signature, String[] exceptions) {
        if (owner.equals(TEXTURE_HELPER) && (access & ACC_STATIC) == 0) {
            if (name.equals("setTextureSize") && desc.equals("(II)V")) {
                sizeMethods++;
            }
            if (name.equals("setFrameRotation") && desc.equals("(I)V")) {
                rotationMethods++;
            }
        }
        MethodVisitor next = super.visitMethod(access, name, desc, signature, exceptions);
        return new MethodVisitor(ASM9, next) {
            @Override
            public void visitCode() {
                super.visitCode();
                // Before any lock is taken: wait briefly for the previous picture to be handed
                // on, so this one is drawn instead of dropped.
                if (owner.equals(DECODER) && name.equals("deliverTextureFrame") && desc.equals(DELIVER_DESC)) {
                    textureGates++;
                    super.visitMethodInsn(INVOKESTATIC, HOOKS, "beforeTexture", "()V", false);
                }
            }

            @Override
            public void visitFieldInsn(int opcode, String target, String field, String descriptor) {
                super.visitFieldInsn(opcode, target, field, descriptor);
                if (owner.equals(DECODER) && name.equals("initDecode")
                        && desc.equals("(Lorg/webrtc/VideoDecoder$Settings;Lorg/webrtc/VideoDecoder$Callback;)Lorg/webrtc/VideoCodecStatus;")
                        && opcode == PUTFIELD && target.equals(DECODER)
                        && field.equals("surfaceTextureHelper")
                        && descriptor.equals("Lorg/webrtc/SurfaceTextureHelper;")) {
                    helperInitializations++;
                    Label ready = new Label();
                    super.visitVarInsn(ALOAD, 0);
                    super.visitFieldInsn(GETFIELD, DECODER, field, descriptor);
                    super.visitJumpInsn(IFNONNULL, ready);
                    super.visitVarInsn(ALOAD, 0);
                    super.visitInsn(ACONST_NULL);
                    super.visitFieldInsn(PUTFIELD, DECODER, "callback", "Lorg/webrtc/VideoDecoder$Callback;");
                    super.visitFieldInsn(GETSTATIC, "org/webrtc/VideoCodecStatus", "ERROR", "Lorg/webrtc/VideoCodecStatus;");
                    super.visitInsn(ARETURN);
                    super.visitLabel(ready);
                }
            }

            @Override
            public void visitMethodInsn(int opcode, String target, String method, String descriptor,
                    boolean isInterface) {
                if (owner.equals(DECODER) && name.equals("initDecodeInternal") && opcode == INVOKESTATIC
                        && target.equals("android/media/MediaFormat") && method.equals("createVideoFormat")
                        && descriptor.equals(FORMAT_DESC)) {
                    formatCalls++;
                    super.visitMethodInsn(INVOKESTATIC, HOOKS, "videoFormat", FORMAT_DESC, false);
                    return;
                }
                if (owner.equals(DECODER) && name.equals("onFrame") && desc.equals("(Lorg/webrtc/VideoFrame;)V")
                        && opcode == INVOKEINTERFACE && target.equals("org/webrtc/VideoDecoder$Callback")
                        && method.equals("onDecodedFrame")) {
                    // The texture's bookkeeping is cleared by now; the call's arguments stay
                    // on the stack untouched.
                    textureHandovers++;
                    super.visitMethodInsn(INVOKESTATIC, HOOKS, "textureDelivered", "()V", false);
                }
                if (owner.equals(DECODER) && name.equals("deliverTextureFrame")
                        && opcode == INVOKEVIRTUAL && target.equals(TEXTURE_HELPER)) {
                    if (method.equals("setTextureSize") && descriptor.equals("(II)V")) {
                        sizeMethods++;
                        super.visitMethodInsn(opcode, target, "cloudlightSetTextureSize", descriptor, isInterface);
                        return;
                    }
                    if (method.equals("setFrameRotation") && descriptor.equals("(I)V")) {
                        rotationMethods++;
                        super.visitMethodInsn(opcode, target, "cloudlightSetFrameRotation", descriptor, isInterface);
                        return;
                    }
                }
                if (opcode == INVOKESPECIAL && target.equals("android/os/HandlerThread")
                        && method.equals("<init>") && descriptor.equals("(Ljava/lang/String;)V")) {
                    threadConstructors++;
                    if (owner.equals(TEXTURE_HELPER)) {
                        // Only decoder delivery gets display priority. Camera/capture helpers
                        // retain their original priority. Leave the constructor's name on stack.
                        super.visitInsn(DUP);
                        super.visitLdcInsn("decoder-texture-thread");
                        super.visitInsn(SWAP);
                        super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals",
                                "(Ljava/lang/Object;)Z", false);
                        Label normal = new Label();
                        Label ready = new Label();
                        super.visitJumpInsn(IFEQ, normal);
                        super.visitIntInsn(BIPUSH, -4); // Process.THREAD_PRIORITY_DISPLAY
                        super.visitJumpInsn(GOTO, ready);
                        super.visitLabel(normal);
                        super.visitInsn(ICONST_0); // HandlerThread's original default priority.
                        super.visitLabel(ready);
                    } else {
                        super.visitIntInsn(BIPUSH, -4); // EGL display worker, below audio priority.
                    }
                    super.visitMethodInsn(opcode, target, method, "(Ljava/lang/String;I)V", isInterface);
                    return;
                }
                super.visitMethodInsn(opcode, target, method, descriptor, isInterface);
            }
        };
    }

    @Override
    public void visitEnd() {
        if (threadConstructors != (owner.equals(DECODER) ? 0 : 1)
                || helperInitializations != (owner.equals(DECODER) ? 1 : 0)
                || ((owner.equals(TEXTURE_HELPER) || owner.equals(DECODER))
                && (sizeMethods != 1 || rotationMethods != 1))) {
            throw new IllegalStateException("WebRTC texture API changed; review texture delivery tuning: "
                    + owner + " size=" + sizeMethods + " rotation=" + rotationMethods
                    + " threadConstructors=" + threadConstructors
                    + " helperInitializations=" + helperInitializations);
        }
        if (owner.equals(DECODER) && (textureGates != 1 || textureHandovers != 1 || formatCalls != 1)) {
            throw new IllegalStateException("WebRTC decoder changed; review the frame hand-over hooks:"
                    + " gates=" + textureGates + " handovers=" + textureHandovers + " formats=" + formatCalls);
        }
        if (owner.equals(TEXTURE_HELPER)) {
            // Only AndroidVideoDecoder uses these entry points. Preserve upstream setters
            // for camera capture and any other users of SurfaceTextureHelper.
            addCachedSetter("cloudlightSetTextureSize", "setTextureSize", "Size", 2);
            addCachedSetter("cloudlightSetFrameRotation", "setFrameRotation", "Rotation", 1);
        }
        super.visitEnd();
    }

    private void addCachedSetter(String methodName, String originalName, String prefix, int args) {
        String valid = "cloudlight" + prefix + "Requested";
        super.visitField(ACC_PRIVATE, valid, "Z", null, null).visitEnd();
        for (int i = 1; i <= args; i++) {
            super.visitField(ACC_PRIVATE, "cloudlight" + prefix + i, "I", null, null).visitEnd();
        }
        String desc = args == 2 ? "(II)V" : "(I)V";
        // Serialize requests only; never wait for the texture worker or hold its EGL locks.
        // Comparing applied values would miss identical calls while the first update is queued.
        MethodVisitor m = super.visitMethod(ACC_PUBLIC | ACC_SYNCHRONIZED, methodName, desc, null, null);
        m.visitCode();
        Label update = new Label();
        m.visitVarInsn(ALOAD, 0);
        m.visitFieldInsn(GETFIELD, owner, valid, "Z");
        m.visitJumpInsn(IFEQ, update);
        for (int i = 1; i <= args; i++) {
            m.visitVarInsn(ALOAD, 0);
            m.visitFieldInsn(GETFIELD, owner, "cloudlight" + prefix + i, "I");
            m.visitVarInsn(ILOAD, i);
            m.visitJumpInsn(IF_ICMPNE, update);
        }
        m.visitInsn(RETURN);
        m.visitLabel(update);
        m.visitVarInsn(ALOAD, 0);
        for (int i = 1; i <= args; i++) m.visitVarInsn(ILOAD, i);
        m.visitMethodInsn(INVOKEVIRTUAL, owner, originalName, desc, false);
        // Cache only successful requests. Invalid sizes and platform failures must still throw.
        for (int i = 1; i <= args; i++) {
            m.visitVarInsn(ALOAD, 0);
            m.visitVarInsn(ILOAD, i);
            m.visitFieldInsn(PUTFIELD, owner, "cloudlight" + prefix + i, "I");
        }
        m.visitVarInsn(ALOAD, 0);
        m.visitInsn(ICONST_1);
        m.visitFieldInsn(PUTFIELD, owner, valid, "Z");
        m.visitInsn(RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }
}
