import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.window.singleWindowApplication
import net.akehurst.kotlin.components.layout.graph.GraphLayout
import net.akehurst.kotlin.components.layout.graph.GraphLayoutState
import net.akehurst.kotlinx.collections.GraphSimple
import kotlin.test.Test


class test_MultiPaneLayout {

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun main() {
        // initial layout
        val graph = GraphSimple("")
        //println(graph.asString(""))
        val layoutState = GraphLayoutState(graph)

        singleWindowApplication(
            title = "Demo MultiPaneLayout",
        ) {
            GraphLayout(
                layoutState = layoutState,

            )
        }
    }
}


