package net.corda.client.rpc.internal.amqp

import net.corda.client.rpc.internal.kryo.KryoClientSerializationScheme
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.AMQP_RPC_CLIENT_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AbstractAMQPSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import java.util.concurrent.atomic.AtomicBoolean

class AMQPClientSerializationScheme : AbstractAMQPSerializationScheme() {
    private fun serializerFactory(context: SerializationContext) =
        SerializerFactory(context.whitelist, context.deserializationClassLoader).apply {
            register(ObservableSerializer)
        }

    override fun rpcClientSerializerFactory(context: SerializationContext) = serializerFactory(context)

    override fun rpcServerSerializerFactory(context: SerializationContext) = serializerFactory(context)

    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase) =
        canDeserializeVersion(byteSequence) && (target != SerializationContext.UseCase.Checkpoint)

    fun createContext(serializationContext: SerializationContext, observableContext: ObservableContext): SerializationContext {
        return serializationContext.withProperty(RpcObservableContextKey, observableContext)
    }

    companion object {
        val isInitialised = AtomicBoolean(false)

        fun initialiseSerialization() {
            if (!isInitialised.compareAndSet(false, true)) return
            try {
                SerializationDefaults.SERIALIZATION_FACTORY = SerializationFactoryImpl().apply {
                    registerScheme(KryoClientSerializationScheme())
                    registerScheme(AMQPClientSerializationScheme())
                }

                SerializationDefaults.P2P_CONTEXT = AMQP_P2P_CONTEXT
                SerializationDefaults.RPC_CLIENT_CONTEXT = AMQP_RPC_CLIENT_CONTEXT
            } catch (e: IllegalStateException) {
                // Check that it's registered as we expect
                val factory = SerializationDefaults.SERIALIZATION_FACTORY
                val checkedFactory = factory as? SerializationFactoryImpl ?: throw IllegalStateException(
                        "RPC client encountered conflicting configuration of serialization subsystem: $factory")

                check(checkedFactory.alreadyRegisteredSchemes.any { it is AMQPClientSerializationScheme }) {
                    "RPC client encountered conflicting configuration of serialization subsystem."
                }
            }
        }
    }
}