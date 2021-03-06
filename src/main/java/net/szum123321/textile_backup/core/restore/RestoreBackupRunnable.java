/*
    A simple backup mod for Fabric
    Copyright (C) 2020  Szum123321

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package net.szum123321.textile_backup.core.restore;

import net.minecraft.server.MinecraftServer;
import net.szum123321.textile_backup.ConfigHandler;
import net.szum123321.textile_backup.core.LivingServer;
import net.szum123321.textile_backup.Statics;
import net.szum123321.textile_backup.core.Utilities;
import net.szum123321.textile_backup.core.create.BackupContext;
import net.szum123321.textile_backup.core.create.BackupHelper;
import net.szum123321.textile_backup.core.restore.decompressors.GenericTarDecompressor;
import net.szum123321.textile_backup.core.restore.decompressors.ZipDecompressor;

import java.io.File;
import java.util.NoSuchElementException;

public class RestoreBackupRunnable implements Runnable {
    private final MinecraftServer server;
    private final File backupFile;
    private final String finalBackupComment;

    public RestoreBackupRunnable(MinecraftServer server, File backupFile, String finalBackupComment) {
        this.server = server;
        this.backupFile = backupFile;
        this.finalBackupComment = finalBackupComment;
    }

    @Override
    public void run() {
        Statics.LOGGER.info("Shutting down server...");
        server.stop(false);
        awaitServerShutdown();

        if(Statics.CONFIG.backupOldWorlds) {
            BackupHelper.create(
                    new BackupContext.Builder()
                            .setServer(server)
                            .setInitiator(BackupContext.BackupInitiator.Restore)
                            .setComment("Old_World" + (finalBackupComment != null ? "_" + finalBackupComment : ""))
                            .build()
            ).run();
        }

        File worldFile = Utilities.getWorldFolder(server);

        Statics.LOGGER.info("Deleting old world...");
        if(!deleteDirectory(worldFile))
            Statics.LOGGER.error("Something went wrong while deleting old world!");

        worldFile.mkdirs();

        Statics.LOGGER.info("Starting decompression...");

        if(Utilities.getFileExtension(backupFile).orElseThrow(() -> new NoSuchElementException("Couldn't get file extension!")) == ConfigHandler.ArchiveFormat.ZIP) {
            ZipDecompressor.decompress(backupFile, worldFile);
        } else {
            GenericTarDecompressor.decompress(backupFile, worldFile);
        }

        if(Statics.CONFIG.deleteOldBackupAfterRestore) {
            Statics.LOGGER.info("Deleting old backup");

            if(!backupFile.delete())
                Statics.LOGGER.info("Something went wrong while deleting old backup");
        }

        Statics.LOGGER.info("Done!");
    }

    private void awaitServerShutdown() {
        while(((LivingServer)server).isAlive()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Statics.LOGGER.error("Exception occurred!", e);
            }
        }
    }

    private static boolean deleteDirectory(File f) {
        boolean state = true;

        if(f.isDirectory()) {
            for(File f2 : f.listFiles())
                state &= deleteDirectory(f2);
        }

        return f.delete() && state;
    }
}