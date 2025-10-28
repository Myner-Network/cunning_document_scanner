//
//  ScannerOptions.swift
//  cunning_document_scanner
//
//  Created by Maurits van Beusekom on 15/10/2024.
//

import Foundation
import AVFoundation

enum CunningScannerImageFormat: String {
    case jpg
    case png
}

enum CunningScannerFlashMode: String {
    case auto
    case on
    case off
    
    func toAVFlashMode() -> AVCaptureDevice.FlashMode {
        switch self {
        case .auto:
            return .auto
        case .on:
            return .on
        case .off:
            return .off
        }
    }
}

struct CunningScannerOptions {
    let imageFormat: CunningScannerImageFormat
    let jpgCompressionQuality: Double
    let flashMode: CunningScannerFlashMode
    
    init() {
        self.imageFormat = CunningScannerImageFormat.png
        self.jpgCompressionQuality = 1.0
        self.flashMode = CunningScannerFlashMode.auto
    }
    
    init(imageFormat: CunningScannerImageFormat) {
        self.imageFormat = imageFormat
        self.jpgCompressionQuality = 1.0
        self.flashMode = CunningScannerFlashMode.auto
    }
    
    init(imageFormat: CunningScannerImageFormat, jpgCompressionQuality: Double) {
        self.imageFormat = imageFormat
        self.jpgCompressionQuality = jpgCompressionQuality
        self.flashMode = CunningScannerFlashMode.auto
    }
    
    init(imageFormat: CunningScannerImageFormat, jpgCompressionQuality: Double, flashMode: CunningScannerFlashMode) {
        self.imageFormat = imageFormat
        self.jpgCompressionQuality = jpgCompressionQuality
        self.flashMode = flashMode
    }
    
    static func fromArguments(args: Any?) -> CunningScannerOptions {
        if (args == nil) {
            return CunningScannerOptions()
        }
        
        let arguments = args as? Dictionary<String, Any>
    
        // Parse flashMode from top-level arguments
        let flashModeString: String = (arguments?["flashMode"] as? String) ?? "auto"
        let flashMode = CunningScannerFlashMode(rawValue: flashModeString) ?? CunningScannerFlashMode.auto
        
        // Parse iOS-specific options if present
        if arguments == nil || arguments!.keys.contains("iosScannerOptions") == false {
            return CunningScannerOptions(
                imageFormat: CunningScannerImageFormat.png,
                jpgCompressionQuality: 1.0,
                flashMode: flashMode
            )
        }
        
        let scannerOptionsDict = arguments!["iosScannerOptions"] as! Dictionary<String, Any>
        let imageFormat: String = (scannerOptionsDict["imageFormat"] as? String) ?? "png"
        let jpgCompressionQuality: Double = (scannerOptionsDict["jpgCompressionQuality"] as? Double) ?? 1.0
            
        return CunningScannerOptions(
            imageFormat: CunningScannerImageFormat(rawValue: imageFormat) ?? CunningScannerImageFormat.png,
            jpgCompressionQuality: jpgCompressionQuality,
            flashMode: flashMode
        )
    }
}
