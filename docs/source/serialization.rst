Object serialization
====================

.. contents::

What is serialization (and deserialization)?
--------------------------------------------

Object serialization is the process of converting objects into a stream of bytes and, deserialization, the reverse
process of creating objects from a stream of bytes.  It takes place every time nodes pass objects to each other as
messages, when objects are sent to or from RPC clients from the node, and when we store transactions in the database.

Whitelisting
------------

In classic Java serialization, any class on the JVM classpath can be deserialized.  This has shown to be a source of exploits
and vulnerabilities by exploiting the large set of 3rd party libraries on the classpath as part of the dependencies of
a JVM application and a carefully crafted stream of bytes to be deserialized. In Corda, we prevent just any class from
being deserialized (and pro-actively during serialization) by insisting that each object's class belongs on a whitelist
of allowed classes.

Classes get onto the whitelist via one of three mechanisms:

#. Via the ``@CordaSerializable`` annotation.  In order to whitelist a class, this annotation can be present on the
   class itself, on any of the super classes or on any interface implemented by the class or super classes or any
   interface extended by an interface implemented by the class or superclasses.
#. By implementing the ``SerializationWhitelist`` interface and specifying a list of `whitelist` classes.
#. Via the built in Corda whitelist (see the class ``DefaultWhitelist``).  Whilst this is not user editable, it does list
   common JDK classes that have been whitelisted for your convenience.

The annotation is the preferred method for whitelisting.  An example is shown in :doc:`tutorial-clientrpc-api`.
It's reproduced here as an example of both ways you can do this for a couple of example classes.

.. literalinclude:: example-code/src/main/kotlin/net/corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 7
    :end-before: END 7

.. note:: Several of the core interfaces at the heart of Corda are already annotated and so any classes that implement
   them will automatically be whitelisted.  This includes `Contract`, `ContractState` and `CommandData`.

.. warning:: Java 8 Lambda expressions are not serializable except in flow checkpoints, and then not by default. The syntax to declare a serializable Lambda
   expression that will work with Corda is ``Runnable r = (Runnable & Serializable) () -> System.out.println("Hello World");``, or
   ``Callable<String> c = (Callable<String> & Serializable) () -> "Hello World";``.

.. warning:: We will be replacing the use of Kryo in the serialization framework and so additional changes here are
   likely.

AMQP
====

Originally Corda used a ``Kryo``-based serialization scheme throughout for all serialization contexts. However, it was realised there
was a compelling use case for the definition and development of a custom format based upon AMQP 1.0. The primary drivers for this were

    #.  A desire to have a schema describing what has been serialized along-side the actual data:

        #.  To assist with versioning, both in terms of being able to interpret long ago archived data (e.g. trades from
            a decade ago, long after the code has changed) and between differing code versions.
        #.  To make it easier to write user interfaces that can navigate the serialized form of data.
        #.  To support cross platform (non-JVM) interaction, where the format of a class file is not so easily interpreted.
    #.  A desire to use a documented and static wire format that is platform independent, and is not subject to change with
        3rd party library upgrades etc.
    #.  A desire to support open-ended polymorphism, where the number of subclasses of a superclass can expand over time
        and do not need to be defined in the schema *upfront*, which is key to many Corda concepts, such as contract states.
    #.  Increased security from deserialized objects being constructed through supported constructors rather than having
        data poked directly into their fields without an opportunity to validate consistency or intercept attempts to manipulate
        supposed invariants.

Delivering this is an ongoing effort by the Corda development team. At present, the ``Kryo``-based format is still used by the RPC framework on
both the client and server side. However, it is planned that this will move to the AMQP framework when ready.

The AMQP framework is currently used for:

    #.  The peer to peer context, representing inter-node communication.
    #.  The persistence layer, representing contract states persisted into the vault.

Finally, for the checkpointing of flows Corda will continue to use the existing ``Kryo`` scheme.

This separation of serialization schemes into different contexts allows us to use the most suitable framework for that context rather than
attempting to force a one size fits all approach. Where ``Kryo`` is more suited to the serialization of a programs stack frames, being more flexible
than our AMQP framework in what it can construct and serialize, that flexibility makes it exceptionally difficult to make secure. Conversely
our AMQP framework allows us to concentrate on a robust a secure framework that can be reasoned about thus made safer with far fewer unforeseen
security holes.

.. note:: Selection of serialization context should, for the most part, be opaque to CorDapp developers, the Corda framework selecting
    the correct context as confugred.

.. For information on our choice of AMQP 1.0, see :doc:`amqp-choice`.  For detail on how we utilise AMQP 1.0 and represent
   objects in AMQP types, see :doc:`amqp-format`.

We describe here what is and will be supported in the Corda AMQP format from the perspective
of CorDapp developers, to allow for CorDapps to take into consideration the future state.  The AMQP serialization format will of
course continue to apply the whitelisting functionality that is already in place and described in :doc:`serialization`.

Core Types
----------

Here we describe the classes and interfaces that the AMQP serialization format will support.

Collection Types
````````````````

The following collection types are supported.  Any implementation of the following will be mapped to *an* implementation of the interface or class on the other end.
e.g. If you, for example, use a Guava implementation of a collection it will deserialize as a different implementation,
but will continue to adhere to the most specific of any of the following interfaces.  You should use only these types
as the declared types of fields and properties, and not the concrete implementation types.  Collections must be used
in their generic form, the generic type parameters will be included in the schema, and the elements type checked against the
generic parameters when deserialized.

::

    java.util.Collection
    java.util.List
    java.util.Set
    java.util.SortedSet
    java.util.NavigableSet
    java.util.NonEmptySet
    java.util.Map
    java.util.SortedMap
    java.util.NavigableMap

However, we will support the concrete implementation types below explicitly and also as the declared type of a field, as
a convenience.

::

    java.util.LinkedHashMap
    java.util.TreeMap
    java.util.EnumSet
    java.util.EnumMap (but only if there is at least one entry)


JVM primitives
``````````````

All the primitive types are supported.

::

    boolean
    byte
    char
    double
    float
    int
    long
    short

Arrays
``````

We also support arrays of any supported type, primitive or otherwise.

JDK Types
`````````

The following types are supported from the JDK libraries.

::

    java.io.InputStream

    java.lang.Boolean
    java.lang.Byte
    java.lang.Character
    java.lang.Class
    java.lang.Double
    java.lang.Float
    java.lang.Integer
    java.lang.Long
    java.lang.Short
    java.lang.StackTraceElement
    java.lang.String
    java.lang.StringBuffer

    java.math.BigDecimal

    java.security.PublicKey

    java.time.DayOfWeek
    java.time.Duration
    java.time.Instant
    java.time.LocalDate
    java.time.LocalDateTime
    java.time.LocalTime
    java.time.Month
    java.time.MonthDay
    java.time.OffsetDateTime
    java.time.OffsetTime
    java.time.Period
    java.time.YearMonth
    java.time.Year
    java.time.ZonedDateTime
    java.time.ZonedId
    java.time.ZoneOffset

    java.util.BitSet
    java.util.Currency
    java.util.UUID

Third Party Types
`````````````````

The following 3rd party types are supported.

::

    kotlin.Unit
    kotlin.Pair

    org.apache.activemq.artemis.api.core.SimpleString

Corda Types
```````````

Classes and interfaces in the Corda codebase annotated with ``@CordaSerializable`` are of course supported.

All Corda exceptions that are expected to be serialized inherit from ``CordaThrowable`` via either ``CordaException``, for
checked exceptions, or ``CordaRuntimeException``, for unchecked exceptions.  Any ``Throwable`` that is serialized but does
not conform to ``CordaThrowable`` will be converted to a ``CordaRuntimeException`` with the original exception type
and other properties retained within it.

Custom Types
------------

Here are the rules to adhere to for support of your own types:

Classes
```````

General Rules
'''''''''''''

    #.  The class must be compiled with parameter names included in the ``.class`` file.  This is the default in Kotlin
        but must be turned on in Java (``-parameters`` command line option to ``javac``).
    #.  The class is annotated with ``@CordaSerializable``.
    #.  The declared types of constructor arguments, getters, and setters must be supported, and where generics are used the
        generic parameter must be a supported type, an open wildcard (``*``), or a bounded wildcard which is currently
        widened to an open wildcard.
    #.  Any superclass must adhere to the same rules, but can be abstract.
    #.  Object graph cycles are not supported, so an object cannot refer to itself, directly or indirectly.

Constructor Instantiation
'''''''''''''''''''''''''

The primary way the AMQP serialization framework for Corda instantiates objects is via a defined constructor. This is
used to first determine which properties of an object are to be serialised then, on deserialization, it is used to
instantiate the object with the serialized values.

This is the recommended design idiom for serializable objects in Corda as it allows for immutable state objects to
be created

    #.  A Java Bean getter for each of the properties in the constructor, with the names matching up.  For example, for a constructor
        parameter ``foo``, there must be a getter called ``getFoo()``.  If the type of ``foo`` is boolean, the getter may
        optionally be called ``isFoo()``.  This is why the class must be compiled with parameter names turned on.
    #.  A constructor which takes all of the properties that you wish to record in the serialized form.  This is required in
        order for the serialization framework to reconstruct an instance of your class.
    #.  If more than one constructor is provided, the serialization framework needs to know which one to use.  The ``@ConstructorForDeserialization``
        annotation can be used to indicate which one.  For a Kotlin class, without the ``@ConstructorForDeserialization`` annotation, the
        *primary constructor* will be selected.

In Kotlin, this maps cleanly to a data class where there getters are synthesized automatically. For example,

.. container:: codeset

    .. sourcecode:: kotlin

        data class Example (val a: Int, val b: String)

Both properties a and b will be included in the serialised form. However, as stated above, properties not mentioned in
the constructor will not be serialised. For example, in the following code property c will not be considered part of the
serialised form

.. container:: codeset

    .. sourcecode:: kotlin

        data class Example (val a: Int, val b: String) {
            var c: Int = 20
        }

        var e = Example (10, "hello")
        e.c = 100;

        val e2 = e.serialize().deserialize() // e2.c will be 20, not 100!!!

Setter Instantiation
''''''''''''''''''''

As an alternative to constructor based initialisation Corda can also determine the important elements of an
object by inspecting the getter and setter methods present on a class. If a class has **only** a default
constructor **and** properties then the serializable properties will be determined by the presence of
both a getter and setter for that property that are both publicly visible. I.e. the class adheres to
the classic *idiom* of mutable JavaBeans.

On deserialization, a default instance will first be created and then, in turn, the setters invoked
on that object to populate the correct values.

For example:

.. container:: codeset

    .. sourcecode:: Java

        class Example {
            private int a;
            private int b;
            private int c;

            public int getA() { return a; }
            public int getB() { return b; }
            public int getC() { return c; }

            public void setA(int a) { this.a = a; }
            public void setB(int b) { this.b = b; }
            public void setC(int c) { this.c = c; }
        }

Inaccessible Private Properties
```````````````````````````````

Whilst the Corda AMQP serialization framework supports private object properties without publicly
accessible getter methods this development idiom is strongly discouraged.

For example.

    .. container:: codeset

        Kotlin:

        .. sourcecode:: kotlin

            data class C(val a: Int, private val b: Int)

        Java:

        .. sourcecode:: Java

            class C {
                public Integer a;
                private Integer b;

                C(Integer a, Integer b) {
                    this.a = a;
                    this.b = b;
                }
            }

When designing stateful objects is should be remembered that they are not, despite appearances, traditional
programmatic constructs. They are signed over, transformed, serialised, and relationally mapped. As such,
all elements should be publicly accessible by design

.. warning:: IDEs will indiciate erroneously that properties can be given something other than public
    visibility. Ignore this as whilst it will work, as discussed above there are many reasons why this isn't
    a good idea and those are beyond the scope of the IDEs inference rules

Providing a public getter, as per the following example, is acceptable

    .. container:: codeset

        Kotlin:

        .. sourcecode:: kotlin

            data class C(val a: Int, private val b: Int) {
                public fun getB() = b
            }

        Java:

        .. sourcecode:: Java

            class C {
                public Integer a;
                private Integer b;

                C(Integer a, Integer b) {
                    this.a = a;
                    this.b = b;
                }

                public Integer getB() {
                    return b;
                }
            }


Enums
`````

    #.  All enums are supported, provided they are annotated with ``@CordaSerializable``.


Exceptions
``````````

The following rules apply to supported ``Throwable`` implementations.

    #.  If you wish for your exception to be serializable and transported type safely it should inherit from either
        ``CordaException`` or ``CordaRuntimeException``.
    #.  If not, the ``Throwable`` will deserialize to a ``CordaRuntimeException`` with the details of the original
        ``Throwable`` contained within it, including the class name of the original ``Throwable``.

Kotlin Objects
``````````````

    #.  Kotlin ``object`` s are singletons and treated differently.  They are recorded into the stream with no properties
        and deserialize back to the singleton instance. Currently, the same is not true of Java singletons,
        and they will deserialize to new instances of the class.
    #.  Kotlin's anonymous ``object`` s are not currently supported. I.e. constructs like:
        ``object : Contract {...}`` will not serialize correctly and need to be re-written as an explicit class declaration.

The Carpenter
`````````````

We will support a class carpenter that can dynamically manufacture classes from the supplied schema when deserializing
in the JVM without the supporting classes on the classpath.  This can be useful where other components might expect to
be able to use reflection over the deserialized data, and also for ensuring classes not on the classpath can be
deserialized without loading potentially malicious code dynamically without security review outside of a fully sandboxed
environment.  A more detailed discussion of the carpenter will be provided in a future update to the documentation.

Future Enhancements
```````````````````

    #.  Java singleton support.  We will add support for identifying classes which are singletons and identifying the
        static method responsible for returning the singleton instance.
    #.  Instance internalizing support.  We will add support for identifying classes that should be resolved against an instances map to avoid
        creating many duplicate instances that are equal.  Similar to ``String.intern()``.

.. Type Evolution:

Type Evolution
--------------

Type evolution is the mechanisms by which classes can be altered over time yet still remain serializable and deserializable across
all versions of the class. This ensures an object serialized with an older idea of what the class "looked like" can be deserialized
and a version of the current state of the class instantiated.

More detail can be found in :doc:`serialization-default-evolution`

Enum Evolution
``````````````
Corda supports interoperability of enumerated type versions. This allows such types to be changed over time without breaking
backward (or forward) compatibility. The rules and mechanisms for doing this are discussed in :doc:`serialization-enum-evolution``



