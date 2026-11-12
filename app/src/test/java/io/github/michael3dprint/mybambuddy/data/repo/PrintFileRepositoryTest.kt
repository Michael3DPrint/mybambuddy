package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.PrintFileApi
import io.github.michael3dprint.mybambuddy.data.api.SlicingApi
import io.github.michael3dprint.mybambuddy.domain.model.BedType
import io.github.michael3dprint.mybambuddy.domain.model.JobOptions
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldAccess
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldModel
import io.github.michael3dprint.mybambuddy.domain.model.PrintFile
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.model.SliceJobState
import io.github.michael3dprint.mybambuddy.domain.model.SliceRequest
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPreset
import io.github.michael3dprint.mybambuddy.domain.model.StagedPrint
import io.github.michael3dprint.mybambuddy.domain.model.TriState
import io.github.michael3dprint.mybambuddy.domain.print.PrintSource
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate
import io.github.michael3dprint.mybambuddy.testutil.AllowAllToolGate
import io.github.michael3dprint.mybambuddy.testutil.TestSettings
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrintFileRepositoryTest {
    @get:org.junit.Rule
    val tmp = org.junit.rules.TemporaryFolder()

    private lateinit var server: MockWebServer
    private val url get() = server.url("/").toString()

    private fun repo(gate: PrinterToolGate = AllowAllToolGate) = PrintFileRepositoryImpl(
        ApiClient.createService(ApiClient.createOkHttpClient(TestSettings(url)), PrintFileApi::class.java, baseUrl = url),
        ApiClient.createService(ApiClient.createOkHttpClient(TestSettings(url)), SlicingApi::class.java, baseUrl = url),
        gate,
        tmp.root,
    )

    private class Bytes(override val name: String, private val data: ByteArray = ByteArray(200_000) { 1 }) : PrintSource {
        override val size: Long = data.size.toLong()
        override fun open(): InputStream = ByteArrayInputStream(data)
    }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private val platesJson = """{"plates":[{"index":1,"name":"Plate","print_time_seconds":8040,"filament_used_grams":31.2,
        "filaments":[{"slot_id":1,"type":"PLA","color":"FFFFFF","used_grams":31.2}]}],"embedded_printer":"Bambu Lab X2D 0.4 nozzle"}"""

    @Test
    fun `a wrong file type is refused before anything is sent`() = runBlocking {
        val result = repo().upload(Bytes("part.stl")) {}
        assertEquals(ApiErrorType.UNSUPPORTED, (result as ApiResult.Error).errorType)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an upload streams the file, reports progress and reads the plates`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":7,"filename":"a.gcode.3mf","metadata":{"sliced_for_model":"X2D"}}"""))
        server.enqueue(MockResponse().setBody(platesJson))
        val progress = mutableListOf<Float>()
        val file = (repo().upload(Bytes("a.gcode.3mf")) { progress += it } as ApiResult.Success).data
        val upload = server.takeRequest()
        assertEquals("POST /api/v1/library/files/", "${upload.method} ${upload.path}")
        assertTrue(upload.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertTrue(upload.bodySize > 200_000)
        assertEquals("/api/v1/library/files/7/plates", server.takeRequest().path)
        assertTrue(progress.isNotEmpty() && progress.last() == 1f)
        assertEquals("X2D", file.slicedForModel)
        assertEquals(8040, file.plates.single().seconds)
        assertEquals("PLA", file.plates.single().filaments.single().type)
        assertTrue(file.isSliced && file.createdHere)
    }

    @Test
    fun `the printer model falls back to the embedded printer preset`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":7,"filename":"a.3mf"}"""))
        server.enqueue(MockResponse().setBody(platesJson))
        val file = (repo().upload(Bytes("a.3mf")) {} as ApiResult.Success).data
        assertEquals("Bambu Lab X2D 0.4 nozzle", file.slicedForModel)
    }

    @Test
    fun `a server refusal of the upload shows its reason and stores nothing`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"Not a valid 3MF file"}"""))
        val result = repo().upload(Bytes("a.3mf")) {} as ApiResult.Error
        assertEquals("Not a valid 3MF file", result.serverDetail)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `when the plates cannot be read the file this flow uploaded is deleted once`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":7,"filename":"a.3mf"}"""))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("{}"))
        assertTrue(repo().upload(Bytes("a.3mf")) {} is ApiResult.Error)
        server.takeRequest()
        server.takeRequest()
        val delete = server.takeRequest()
        assertEquals("DELETE /api/v1/library/files/7", "${delete.method} ${delete.path}")
    }

    @Test
    fun `staging always asks for a staged item`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":42}"""))
        val id = (repo().stage(StagedPrint(7, 1, 1, listOf(5, -1), cleanup = true)) as ApiResult.Success).data
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals(42, id)
        assertEquals("POST /api/v1/queue/", "${request.method} ${request.path}")
        assertTrue(body.contains("\"manual_start\":true"))
        assertTrue(body.contains("\"cleanup_library_after_dispatch\":true"))
        assertTrue(body.contains("\"ams_mapping\":[5,-1]"))
        assertFalse(body.contains("insert_position") || body.contains("bed_levelling"))
        assertTrue(body.contains("\"plate_id\":1") && body.contains("\"printer_id\":1") && body.contains("\"library_file_id\":7"))
    }

    @Test
    fun `start sends once, skips the filament check only when told to, and a refusal sends nothing`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        repo().start(42, 1, skipFilamentCheck = false)
        repo().start(42, 1, skipFilamentCheck = true)
        assertEquals("/api/v1/queue/42/start?skip_filament_check=false", server.takeRequest().path)
        assertEquals("/api/v1/queue/42/start?skip_filament_check=true", server.takeRequest().path)
        val deny = object : PrinterToolGate {
            override suspend fun refusal(printerId: String, tool: PrinterTool, printerName: String) = "Developer LAN mode is off."
        }
        val refused = repo(deny).start(42, 1, skipFilamentCheck = false) as ApiResult.Error
        assertEquals("Developer LAN mode is off.", refused.message)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a filament shortfall comes back as a 409 with the server's reason`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"Slot 2 is short by 12 g"}"""))
        val result = repo().start(42, 1, skipFilamentCheck = false) as ApiResult.Error
        assertEquals(409, result.code)
        assertEquals("Slot 2 is short by 12 g", result.serverDetail)
    }

    @Test
    fun `makerworld access reflects the server's sign in`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"has_cloud_token":true,"can_download":true}"""))
        server.enqueue(MockResponse().setBody("""{"has_cloud_token":false,"can_download":false}"""))
        server.enqueue(MockResponse().setBody("""{"has_cloud_token":true,"can_download":false,"sign_in_expired":true}"""))
        val r = repo()
        assertEquals(MakerWorldAccess.READY, (r.makerWorldAccess() as ApiResult.Success).data)
        assertEquals(MakerWorldAccess.NOT_SIGNED_IN, (r.makerWorldAccess() as ApiResult.Success).data)
        assertEquals(MakerWorldAccess.SIGN_IN_EXPIRED, (r.makerWorldAccess() as ApiResult.Success).data)
    }

    @Test
    fun `a link that is not makerworld is refused without a request`() = runBlocking {
        assertTrue(repo().resolveMakerWorld("https://example.com/x") is ApiResult.Error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `resolve and import go through the server and the import is not ours when it already existed`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"model_id":12345,"design":{"title":"Benchy","coverUrl":"https://public-cdn.bblmw.com/c.png"},"instances":[{"profileId":678,"title":"0.2mm","cover":"https://public-cdn.bblmw.com/p.png"},{"title":"no id"}],
                "already_imported_library_ids":[9]}""",
            ),
        )
        val model = (repo().resolveMakerWorld("https://makerworld.com/en/models/12345") as ApiResult.Success).data
        val resolve = server.takeRequest()
        assertEquals("POST /api/v1/makerworld/resolve", "${resolve.method} ${resolve.path}")
        assertEquals("Benchy", model.title)
        assertEquals(1, model.instances.size)
        assertEquals(678, model.instances.single().profileId)
        assertEquals("https://public-cdn.bblmw.com/c.png", model.coverUrl)
        assertEquals("https://public-cdn.bblmw.com/p.png", model.instances.single().coverUrl)

        server.enqueue(MockResponse().setBody("""{"library_file_id":9,"filename":"Benchy.gcode.3mf"}"""))
        server.enqueue(MockResponse().setBody(platesJson))
        val file = (repo().importMakerWorld(model, 678) as ApiResult.Success).data
        val import = server.takeRequest()
        assertEquals("POST /api/v1/makerworld/import", "${import.method} ${import.path}")
        assertTrue(import.body.readUtf8().contains("\"model_id\":12345"))
        assertEquals(false, file.createdHere)
    }

    @Test
    fun `a new import is ours to clean up`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"library_file_id":11,"filename":"B.3mf"}"""))
        server.enqueue(MockResponse().setBody(platesJson))
        val model = MakerWorldModel(1, "B", emptyList(), emptyList())
        assertEquals(true, (repo().importMakerWorld(model, null) as ApiResult.Success).data.createdHere)
    }

    @Test
    fun `discard deletes one library file`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        repo().discard(7)
        assertEquals("DELETE /api/v1/library/files/7", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test
    fun `presets are flattened across tiers with their ids`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"standard":{"printer":[{"id":"Bambu Lab X2D","name":"Bambu Lab X2D","source":"standard"}],"process":[],"filament":[]},
                "cloud":{"printer":[{"id":"GM045","name":"Bambu Lab X2D 0.4 nozzle","source":"cloud"},{"name":"no id"}],
                "process":[{"id":"GP1","name":"0.20mm Standard @BBL X2D","source":"cloud","compatible_printers":["Bambu Lab X2D 0.4 nozzle"]}],
                "filament":[{"id":"F1","name":"Bambu PLA Basic","filament_type":"PLA"}]},"cloud_status":"ok"}""",
            ),
        )
        val presets = (repo().slicerPresets() as ApiResult.Success).data
        assertEquals("/api/v1/slicer/presets", server.takeRequest().path)
        assertEquals(listOf("standard", "cloud"), presets.printers.map { it.source })
        assertEquals("GM045", presets.printers[1].id)
        assertEquals(listOf("Bambu Lab X2D 0.4 nozzle"), presets.processes.single().compatiblePrinters)
        assertEquals("cloud", presets.filaments.single().source)
        assertEquals("PLA", presets.filaments.single().type)
    }

    @Test
    fun `project slots ask for the full list and a slice sends presets, colours and a 3mf request once`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"filaments":[{"slot_id":1,"type":"PLA","color":"FF0000","used_in_plate":true},{"slot_id":2,"type":"PETG","used_in_plate":false}]}""",
            ),
        )
        val slots = (repo().projectSlots(7, 1) as ApiResult.Success).data
        assertEquals("/api/v1/library/files/7/filament-requirements?plate_id=1&full_slots=true", server.takeRequest().path)
        assertEquals(listOf(true, false), slots.map { it.usedInPlate })

        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"job_id":3,"status":"pending"}"""))
        val preset = SlicerPreset("cloud", "GM045", "Bambu Lab X2D 0.4 nozzle", null, emptyList())
        val job = (
            repo().startSlice(
                7,
                SliceRequest(preset, preset, listOf(preset, preset), listOf("#FF0000", ""), 1, BedType.TEXTURED_PEI),
            ) as ApiResult.Success
            ).data
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals(3, job)
        assertEquals("POST /api/v1/library/files/7/slice", "${request.method} ${request.path}")
        assertTrue(body.contains("\"export_3mf\":true"))
        assertTrue(body.contains("\"bed_type\":\"Textured PEI Plate\""))
        assertTrue(body.contains("\"auto_orient\":false") && body.contains("\"auto_arrange\":false"))
        assertTrue(body.contains("\"use_embedded_settings\":false"))
        assertTrue(body.contains("\"printer_preset\":{\"source\":\"cloud\",\"id\":\"GM045\"}"))
        assertTrue(body.contains("\"filament_colours\":[\"#FF0000\",\"\"]"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a slice job is read once and a finished one carries the new file`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"completed","result":{"library_file_id":12,"name":"a.gcode.3mf"}}"""))
        val state = (repo().sliceJob(3) as ApiResult.Success).data
        assertEquals("/api/v1/slice-jobs/3", server.takeRequest().path)
        assertEquals(SliceJobState.Done(12, "a.gcode.3mf"), state)
    }

    @Test
    fun `opening a library file reads its plates and keeps the extra files to clean up`() = runBlocking {
        server.enqueue(MockResponse().setBody(platesJson))
        val file = (repo().open(12, "a.gcode.3mf", createdHere = true, alsoCreated = listOf(7)) as ApiResult.Success).data
        assertEquals(listOf(7), file.alsoCreated)
        assertTrue(file.isSliced)
    }

    @Test
    fun `the preview comes from the plate picture when the file has one, else from the file thumbnail`() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(9, 8, 7))))
        val plate = (repo().preview(7, 2, hasPlatePicture = true) as ApiResult.Success).data
        assertEquals("/api/v1/library/files/7/plate-thumbnail/2", server.takeRequest().path)
        assertEquals(3, plate.size)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(1))))
        repo().preview(7, 1, hasPlatePicture = false)
        assertEquals("/api/v1/library/files/7/thumbnail", server.takeRequest().path)
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(repo().preview(7, 1, hasPlatePicture = false) is ApiResult.Error)
    }

    @Test
    fun `a makerworld picture is fetched through bambuddy and nothing goes to makerworld from the phone`() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(4, 4, 4))))
        val bytes = (repo().makerWorldImage("https://public-cdn.bblmw.com/c.png") as ApiResult.Success).data
        val request = server.takeRequest()
        assertEquals(3, bytes.size)
        assertEquals("GET", request.method)
        assertTrue(request.path!!.startsWith("/api/v1/makerworld/thumbnail?url=https%3A%2F%2Fpublic-cdn.bblmw.com%2Fc.png"))
    }

    @Test
    fun `a replacement job is staged at the old place with the carried print options`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":43}"""))
        val options = JobOptions(bedLevelling = TriState.OFF, timelapse = true)
        repo().stage(StagedPrint(8, 1, 1, listOf(5), cleanup = true, insertPosition = 2, options = options))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"manual_start\":true"))
        assertTrue(body.contains("\"insert_position\":2"))
        assertTrue(body.contains("\"bed_levelling\":\"off\"") && body.contains("\"timelapse\":true"))
    }

    @Test
    fun `an archived print is read like a file that is not ours to delete`() = runBlocking {
        server.enqueue(MockResponse().setBody(platesJson))
        val file = (repo().openArchive(1, "Ornament") as ApiResult.Success).data
        assertEquals("/api/v1/archives/1/plates", server.takeRequest().path)
        assertEquals(io.github.michael3dprint.mybambuddy.domain.model.FileOrigin.ARCHIVE, file.origin)
        assertEquals(false, file.createdHere)
        assertTrue(file.isSliced)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(1))))
        repo().archivePreview(1, 2, hasPlatePicture = true)
        assertEquals("/api/v1/archives/1/plate-thumbnail/2", server.takeRequest().path)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(1))))
        repo().archivePreview(1, 1, hasPlatePicture = false)
        assertEquals("/api/v1/archives/1/thumbnail", server.takeRequest().path)
    }

    @Test
    fun `a job from an archive is staged with the archive id and no library id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":44}"""))
        repo().stage(StagedPrint(1, 1, 1, listOf(5), cleanup = false, fromArchive = true))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"archive_id\":1"))
        assertFalse(body.contains("library_file_id"))
        assertTrue(body.contains("\"manual_start\":true"))
        assertTrue(body.contains("\"cleanup_library_after_dispatch\":false"))
    }

    private fun cubeZip(): ByteArray {
        val pts = listOf(-5 to -5, 5 to -5, 5 to 5, -5 to 5).flatMap { (x, y) -> listOf(Triple(x, y, -5), Triple(x, y, 5)) }
        val verts = pts.joinToString("") { "<vertex x=\"${it.first}\" y=\"${it.second}\" z=\"${it.third}\"/>" }
        val model = "<model><resources><object id=\"1\"><mesh><vertices>$verts</vertices></mesh></object></resources>" +
            "<build><item objectid=\"1\" transform=\"1 0 0 0 1 0 0 0 1 100 100 5\"/></build></model>"
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("3D/3dmodel.model"))
            z.write(model.toByteArray())
            z.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun `a scaled copy is downloaded, rewritten and uploaded under a new name, leaving no temp files`() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(cubeZip())))
        server.enqueue(MockResponse().setBody("""{"id":11,"filename":"Benchy-scaled-150.3mf"}"""))
        server.enqueue(MockResponse().setBody(platesJson))
        val source = PrintFile(7, "Benchy.gcode.3mf", null, emptyList(), createdHere = true)
        val copy = (repo().scaledCopy(source, 150) as ApiResult.Success).data
        val download = server.takeRequest()
        assertEquals("GET /api/v1/library/files/7/download", "${download.method} ${download.path}")
        val upload = server.takeRequest()
        assertEquals("POST /api/v1/library/files/", "${upload.method} ${upload.path}")
        assertTrue(upload.body.readUtf8().contains("Benchy-scaled-150.3mf"))
        assertEquals(11, copy.libraryId)
        assertTrue(copy.createdHere)
        assertEquals(0, tmp.root.walkTopDown().filter { it.isFile }.count())
    }

    @Test
    fun `a download that is not a 3mf is refused with a reason and nothing is uploaded`() = runBlocking {
        server.enqueue(MockResponse().setBody("this is not a zip"))
        val source = PrintFile(7, "Benchy.3mf", null, emptyList(), createdHere = true)
        val result = repo().scaledCopy(source, 150) as ApiResult.Error
        assertEquals(ApiErrorType.UNSUPPORTED, result.errorType)
        assertTrue(result.message.isNotBlank())
        assertEquals(1, server.requestCount)
        assertEquals(0, tmp.root.walkTopDown().filter { it.isFile }.count())
    }
}
