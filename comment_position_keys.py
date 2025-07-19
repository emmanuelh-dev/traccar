import os
import re

# Lista de constantes a comentar
keys_to_comment = [
    'Position.KEY_RPM',
    'Position.KEY_ODOMETER',
    'Position.KEY_IGNITION',
    'Position.KEY_ENGINE_LOAD',
    'Position.KEY_COOLANT_TEMP',
    'Position.KEY_THROTTLE',
    'Position.KEY_FUEL_LEVEL',
    'Position.KEY_OBD_SPEED',
    'Position.KEY_DTCS',
    'Position.KEY_SPEED_LIMIT',
    'Position.KEY_ODOMETER_TRIP',
    'Position.KEY_ODOMETER_SERVICE'
]

# Directorio base
base_dir = r'c:\Users\Emmanuel\Documents\GitHub\traccar\src\main\java\org\traccar\protocol'

def comment_lines_in_file(file_path):
    """Comenta las líneas que contienen las constantes especificadas"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        modified = False
        for i, line in enumerate(lines):
            # Buscar si la línea contiene alguna de las constantes
            for key in keys_to_comment:
                if key in line and not line.strip().startswith('//'):
                    # Comentar la línea
                    lines[i] = '        // ' + line.lstrip()
                    modified = True
                    break
        
        if modified:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.writelines(lines)
            print(f'Modificado: {file_path}')
        
    except Exception as e:
        print(f'Error procesando {file_path}: {e}')

# Lista de archivos a procesar (basado en la búsqueda anterior)
files_to_process = [
    'PortmanProtocolDecoder.java',
    'Tlt2hProtocolDecoder.java',
    'RadarProtocolDecoder.java',
    'WliProtocolDecoder.java',
    'NiotProtocolDecoder.java',
    'SuntechProtocolDecoder.java',
    'PricolProtocolDecoder.java',
    'MeitrackProtocolDecoder.java',
    'PuiProtocolDecoder.java',
    'SanavProtocolDecoder.java',
    'T55ProtocolDecoder.java',
    'VltProtocolDecoder.java',
    'Vt200ProtocolDecoder.java',
    'TmgProtocolDecoder.java',
    'MobilogixProtocolDecoder.java',
    'PositrexProtocolDecoder.java',
    'Pt502ProtocolDecoder.java',
    'StartekProtocolDecoder.java',
    'RaceDynamicsProtocolDecoder.java',
    'Xt2400ProtocolDecoder.java',
    'MeiligaoProtocolDecoder.java',
    'MegastekProtocolDecoder.java',
    'Jt600ProtocolDecoder.java',
    'VtfmsProtocolDecoder.java',
    'IotmProtocolDecoder.java',
    'NavisetProtocolDecoder.java',
    'XexunProtocolDecoder.java',
    'MtxProtocolDecoder.java',
    'TeltonikaProtocolDecoder.java',
    'UuxProtocolDecoder.java',
    'UproProtocolDecoder.java',
    'SabertekProtocolDecoder.java',
    'RuptelaProtocolDecoder.java',
    'TranSyncProtocolDecoder.java',
    'Mta6ProtocolDecoder.java',
    'OigoProtocolDecoder.java',
    'TotemProtocolDecoder.java',
    'L100ProtocolDecoder.java',
    'PacificTrackProtocolDecoder.java',
    'Stl060ProtocolDecoder.java',
    'MxtProtocolDecoder.java',
    'ItsProtocolDecoder.java',
    'StarLinkProtocolDecoder.java',
    'IntellitracProtocolDecoder.java',
    'RitiProtocolDecoder.java',
    'PretraceProtocolDecoder.java',
    'RstProtocolDecoder.java',
    'TytanProtocolDecoder.java',
    'ThurayaProtocolDecoder.java',
    'LacakProtocolDecoder.java',
    'UlbotechProtocolDecoder.java',
    'NavigilProtocolDecoder.java',
    'NdtpV6ProtocolDecoder.java',
    'Xexun2ProtocolDecoder.java',
    'LeafSpyProtocolDecoder.java',
    'NavtelecomProtocolDecoder.java',
    'OwnTracksProtocolDecoder.java',
    'SiwiProtocolDecoder.java',
    'SkypatrolProtocolDecoder.java',
    'TeraTrackProtocolDecoder.java',
    'OmnicommProtocolDecoder.java',
    'VisiontekProtocolDecoder.java',
    'NyitechProtocolDecoder.java',
    'NavisProtocolDecoder.java',
    'Minifinder2ProtocolDecoder.java',
    'M2cProtocolDecoder.java',
    'TelicProtocolDecoder.java',
    'PluginProtocolDecoder.java',
    'T622IridiumProtocolDecoder.java',
    'MaestroProtocolDecoder.java',
    'NetProtocolDecoder.java',
    'Ivt401ProtocolDecoder.java',
    'KhdProtocolDecoder.java',
    'NoranProtocolDecoder.java',
    'TaipProtocolDecoder.java',
    'LaipacProtocolDecoder.java',
    'TechtoCruzProtocolDecoder.java',
    'SviasProtocolDecoder.java',
    'MilesmateProtocolDecoder.java',
    'VnetProtocolDecoder.java',
    'T800xProtocolDecoder.java',
    'JidoProtocolDecoder.java',
    'TzoneProtocolDecoder.java',
    'SupermateProtocolDecoder.java',
    'TrakMateProtocolDecoder.java',
    'WondexProtocolDecoder.java',
    'TrvProtocolDecoder.java',
    'PstProtocolDecoder.java',
    'Tk103ProtocolDecoder.java',
    'TramigoProtocolDecoder.java',
    'ProgressProtocolDecoder.java',
    'Xexun3ProtocolDecoder.java',
    'XirgoProtocolDecoder.java',
    'StarcomProtocolDecoder.java'
]

# Procesar archivos
for filename in files_to_process:
    file_path = os.path.join(base_dir, filename)
    if os.path.exists(file_path):
        comment_lines_in_file(file_path)
    else:
        print(f'Archivo no encontrado: {file_path}')

# También procesar el archivo MotionProcessor.java
motion_processor_path = r'c:\Users\Emmanuel\Documents\GitHub\traccar\src\main\java\org\traccar\session\state\MotionProcessor.java'
if os.path.exists(motion_processor_path):
    comment_lines_in_file(motion_processor_path)

# También procesar el archivo OverspeedProcessor.java
overspeed_processor_path = r'c:\Users\Emmanuel\Documents\GitHub\traccar\src\main\java\org\traccar\session\state\OverspeedProcessor.java'
if os.path.exists(overspeed_processor_path):
    comment_lines_in_file(overspeed_processor_path)

print('Proceso completado.')