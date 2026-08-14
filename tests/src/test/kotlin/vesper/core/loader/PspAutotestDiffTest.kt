package vesper.core.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain

class PspAutotestDiffTest : StringSpec({
    "renders both sides of a changed line" {
        val diff = PspAutotestDiff.render("same\nexpected\n", "same\nactual\n")
        diff shouldContain "- expected"
        diff shouldContain "+ actual"
    }
})
