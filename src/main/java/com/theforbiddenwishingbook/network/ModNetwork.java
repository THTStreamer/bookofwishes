package com.theforbiddenwishingbook.network;

import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetwork {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TheForbiddenWishingBook.MOD_ID)
                .versioned("1.0");

        registrar.playToClient(
                OpenBookScreenPayload.TYPE,
                OpenBookScreenPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() ->
                            com.theforbiddenwishingbook.client.ClientEvents.handleOpenBookScreen(payload, context)
                    );
                }
        );

        registrar.playToServer(
                WishSubmissionPayload.TYPE,
                WishSubmissionPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() ->
                            com.theforbiddenwishingbook.server.WishProcessingManager.handleWishSubmission(payload, context)
                    );
                }
        );

        registrar.playToClient(
                WishResponsePayload.TYPE,
                WishResponsePayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() ->
                            com.theforbiddenwishingbook.client.ClientEvents.handleWishResponse(payload, context)
                    );
                }
        );
    }
}
