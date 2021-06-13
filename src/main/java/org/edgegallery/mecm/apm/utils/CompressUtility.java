package org.edgegallery.mecm.apm.utils;

import static org.edgegallery.mecm.apm.utils.FileChecker.sanitizeFileName;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.edgegallery.mecm.apm.exception.ApmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompressUtility {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompressUtility.class);
    static final int TOO_MANY = 1024;
    static final int TOO_BIG = 104857600;

    private CompressUtility() {
    }

    /**
     * Returns software image descriptor content in string format.
     *
     * @param localFilePath CSAR file path
     * @param intendedDir   intended directory
     */
    public static void unzipApplicationPacakge(String localFilePath, String intendedDir) {

        LOGGER.debug("unzip package....");
        try (ZipFile zipFile = new ZipFile(localFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            int entriesCount = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entriesCount > TOO_MANY) {
                    throw new IllegalStateException("too many files to unzip");
                }
                entriesCount++;
                // sanitize file path
                String fileName = sanitizeFileName(entry.getName(), intendedDir);
                if (!entry.isDirectory()) {
                    try (InputStream inputStream = zipFile.getInputStream(entry)) {
                        if (inputStream.available() > TOO_BIG) {
                            throw new IllegalStateException("file being unzipped is too big");
                        }
                        FileUtils.copyInputStreamToFile(inputStream, new File(fileName));
                        LOGGER.info("unzip package... {}", entry.getName());
                    }
                } else {

                    File dir = new File(fileName);
                    boolean dirStatus = dir.mkdirs();
                    LOGGER.debug("creating dir {}, status {}", fileName, dirStatus);
                }
            }
        } catch (IOException e) {
            LOGGER.error(Constants.FAILED_TO_UNZIP_CSAR);
            throw new ApmException(Constants.FAILED_TO_UNZIP_CSAR);
        }
    }

    /**
     * Decompress tar file.
     *
     * @param tarFile  tar file
     * @param destFile destination folder
     */
    public static void deCompress(String tarFile, File destFile) {
        TarArchiveInputStream tis = null;
        try (FileInputStream fis = new FileInputStream(tarFile)) {

            if (tarFile.contains(".tar")) {
                tis = new TarArchiveInputStream(new BufferedInputStream(fis));
            } else {
                GZIPInputStream gzipInputStream = new GZIPInputStream(new BufferedInputStream(fis));
                tis = new TarArchiveInputStream(gzipInputStream);
            }

            TarArchiveEntry tarEntry;
            while ((tarEntry = tis.getNextTarEntry()) != null) {
                if (tarEntry.isDirectory()) {
                    LOGGER.debug("skip directory");
                } else {
                    if (!tarEntry.isDirectory()) {

                        File outputFile = new File(destFile + File.separator + tarEntry.getName());
                        LOGGER.info("deCompressing... {}", outputFile.getName());
                        boolean result = outputFile.getParentFile().mkdirs();
                        LOGGER.debug("create directory result {}", result);
                        FileOutputStream fos = new FileOutputStream(outputFile);
                        IOUtils.copy(tis, fos);
                        fos.close();
                    }
                }
            }
        } catch (IOException ex) {
            throw new ApmException("failed to decompress, IO exception " + ex.getMessage());
        } finally {
            if (tis != null) {
                try {
                    tis.close();
                } catch (IOException ex) {
                    LOGGER.error("failed to close tar input stream {} ", ex.getMessage());
                }
            }
        }
    }

    /**
     * Compress the source path files into tar file.
     *
     * @param sourceDir source path
     * @param destDir destination path
     */
    public static void compress(String sourceDir, String destDir) {
        if (sourceDir == null || sourceDir.isEmpty()) {
            return;
        }
        File destFile = new File(destDir);
        try (FileOutputStream destOutStream = new FileOutputStream(destFile.getCanonicalPath());
             GzipCompressorOutputStream gipOutStream = new GzipCompressorOutputStream(
                     new BufferedOutputStream(destOutStream));
             TarArchiveOutputStream outStream = new TarArchiveOutputStream(gipOutStream)) {

            addFileToTar(sourceDir, "", outStream);

        } catch (IOException e) {
            throw new ApmException("failed to compress " + e.getMessage());
        }
    }

    private static void addFileToTar(String filePath, String parent,
                                     TarArchiveOutputStream tarArchive) throws IOException {

        File file = new File(filePath);
        LOGGER.info("compressing... {}", file.getName());
        FileInputStream inputStream = null;
        String entry = parent + file.getName();
        try {
            tarArchive.putArchiveEntry(new TarArchiveEntry(file, entry));
            if (file.isFile()) {
                inputStream = new FileInputStream(file);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);

                IOUtils.copy(bufferedInputStream, tarArchive);
                tarArchive.closeArchiveEntry();
                bufferedInputStream.close();
            } else if (file.isDirectory()) {
                tarArchive.closeArchiveEntry();
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        addFileToTar(f.getAbsolutePath(), entry + File.separator, tarArchive);
                    }
                }
            }
        } catch (IOException e) {
            throw new ApmException("failed to compress " + e.getMessage());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    /**
     * ZIP application package.
     *
     * @param sourceDir source path
     * @param destDir   destination path
     */
    public static void compressAppPackage(String sourceDir, String destDir) {
        LOGGER.info("Compress application package");
        final Path srcDir = Paths.get(sourceDir);
        String zipFileName = sourceDir.concat(".csar");
        try (ZipOutputStream os = new ZipOutputStream(new FileOutputStream(zipFileName))) {
            Files.walkFileTree(srcDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
                        throws IOException {
                    if (!srcDir.equals(dir)) {
                        os.putNextEntry(new ZipEntry(srcDir.relativize(dir).toString() + "/"));
                        os.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    try {
                        Path targetFile = srcDir.relativize(file);
                        os.putNextEntry(new ZipEntry(targetFile.toString()));
                        byte[] bytes = Files.readAllBytes(file);
                        os.write(bytes, 0, bytes.length);
                        os.closeEntry();
                    } catch (IOException e) {
                        throw new ApmException("failed to zip application package");
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new ApmException("failed to zip application package IO exception");
        }
        try {
            FileUtils.deleteQuietly(new File(sourceDir));
            FileUtils.forceMkdir(new File(sourceDir));
            FileUtils.moveFile(new File(zipFileName), new File(destDir));

        } catch (IOException e) {
            throw new ApmException("failed to delete redundant files from app package");
        }
        LOGGER.info("compressed application package successfully");
    }
}
