package io.github.davidru85.multiverse.core.presentation

/**
 * The names under which the composition roots bind the app-wide presentation dependencies
 * (`DEC-145`): each is bound **once**, by the Android shell and by `IosGraph`, and a feature resolves it
 * by name rather than declaring its own. A feature module therefore never changes what another
 * feature receives, whatever modules load and in whatever order.
 *
 * They are plain names so this module stays free of the DI library; the roots and the features turn
 * them into qualifiers.
 */
public object PresentationBindings {
    /** The dispatcher a shared state holder runs its work on: `Dispatchers.Default`. */
    public const val DEFAULT_DISPATCHER: String = "multiverse.dispatcher.default"

    /** The dispatcher a platform holder publishes on: the platform's main thread. */
    public const val MAIN_DISPATCHER: String = "multiverse.dispatcher.main"
}
