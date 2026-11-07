package com.Fynsh.ccf;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 模组数据附件（Data Attachment）注册。
 *
 * <p>NeoForge 1.20.5+ 移除了 Forge 的 {@code Entity#getPersistentData()}，
 * 因此跟随掉落物的标记改为存储在一个 {@link CompoundTag} 附件中。
 * 附件会随实体一起持久化，但不需要同步到客户端。</p>
 */
public final class CCFAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CCFMod.MODID);

    /**
     * 掉落物的跟随状态。
     *
     * <p>键：{@link CCFFollowHandler#TAG_FOLLOWING}（boolean）、
     * {@link CCFFollowHandler#TAG_FOLLOW_TICKS}（int）。</p>
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CompoundTag>> FOLLOW_DATA =
            ATTACHMENT_TYPES.register("follow_data", () -> AttachmentType.builder(() -> new CompoundTag())
                    .serialize(CompoundTag.CODEC)
                    .build());

    private CCFAttachments() {
    }
}
